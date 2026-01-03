package com.peter.velocityrouter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.ServerKickResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;

@Plugin(id = "velocityrouter", name = "Velocity Router", version = "1.2.0", authors = {"Platratio34"})
public class VelocityRouter {

    public final ProxyServer proxyServer;
    public final Logger logger;
    public final Path dataDirectory;

    protected RoutingTable routingTable;

    protected Config config;

    protected static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ForcedHostSet.class, new ForcedHostSet.ForcedHostSetSerializer())
            .registerTypeAdapter(ForcedHostSet.class, new ForcedHostSet.ForcedHostSetDeserializer())
            .setPrettyPrinting()
            .create();

    @Inject
    public VelocityRouter(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        logger.info("Loading Velocity Router");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("Could not create data directory for plugin", e);
        }

        logger.info("Using file-based routing table for Velocity Router");
        routingTable = new FileRoutingTable(dataDirectory.resolve("routing.json"), logger);

        CommandMeta commandMeta = proxyServer.getCommandManager().metaBuilder("velocityrouter").plugin(this).build();
        proxyServer.getCommandManager().register(commandMeta, RouterCommand.create(this));

        reloadConfig();
    }

    protected boolean canJoin(User user, String server) {
        if (user == null)
            return true;
        if (config == null || config.joinPermission.length() == 0)
            return true;
        QueryOptions qo = QueryOptions.contextual(MutableContextSet.of("server", server));
        Collection<Node> nodes = user.resolveInheritedNodes(qo);
        for (Node node : nodes) {
            if (node.getKey().equals(config.joinPermission) && node.getValue())
                return true;
        }
        return false;
    }

    @Subscribe
    public void onServerChooseEvent(PlayerChooseInitialServerEvent chooseServerEvent) {
        Player player = chooseServerEvent.getPlayer();
        ProtocolVersion playerVersion = player.getProtocolVersion();

        User user = null;
        if (config.useLP) {
            try {
                LuckPerms lpApi = LuckPermsProvider.get();
                UserManager um = lpApi.getUserManager();
                user = um.loadUser(player.getUniqueId()).join();
            } catch (IllegalStateException e) {
                logger.warn("Could not load luckperms API, no permission checks will happen");
            }
        }

        if (user != null) {
            QueryOptions qo = QueryOptions.nonContextual();
            Collection<Node> nodes = user.resolveInheritedNodes(qo);
            boolean had = false;
            for (Node node : nodes) {
                if (node.getKey().equals(config.joinPermission) && node.getValue()) {
                    had = true;
                    break;
                }
            }
            if (!had) {
                logger.warn("Could not find a server for player {} in {}", player.getUsername(), playerVersion.name());
                player.disconnect(Component.text(config.noPermDisconnect));
                return;
            }
        }

        // logger.info("DEBUG: Player join");
        // logger.info("DEBUG: host {}", chooseServerEvent.getPlayer().getRawVirtualHost());
        // if (chooseServerEvent.getInitialServer().isPresent()) {
        //     RegisteredServer server = chooseServerEvent.getInitialServer().get();
        //     logger.info("DEBUG: initial server {}", server.getServerInfo().getName());
        // }

        Optional<String> host = chooseServerEvent.getPlayer().getRawVirtualHost();
        if (host.isPresent() && config.forcedHostSet.has(host.get())) {
            String serverId = config.forcedHostSet.get(host.get());
            logger.debug("Player {} had a forced server {}", player.getUsername(), serverId);
            if(!serverId.equals("") && canJoin(user, serverId)) {
                Optional<RegisteredServer> opt = proxyServer.getServer(serverId);
                if(opt.isPresent()) {
                    RegisteredServer server = opt.get();
                    try {
                        if(server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
                            try {
                                server.ping().join();
                                chooseServerEvent.setInitialServer(server);
                                logger.debug("Routing player {} in {} to forced server {}", player.getUsername(), playerVersion.name(), server.getServerInfo().getName());
                                return;
                            } catch(CancellationException|CompletionException exception) {
                                logger.error(String.format("Unable to route %s to forced server of %s; using fallback", player.getUsername(), server.getServerInfo().getName()), exception);
                            }
                        } else {
                            logger.warn("Unable to route {} to forced server of {}: incompatible version;", player.getUsername(), server.getServerInfo().getName());
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error(String.format("Unable to route %s to forced server of %s; using fallback", player.getUsername(), server.getServerInfo().getName()), e);
                    }
                }
            }
        }

        if(routingTable != null) { 
            String serverId = routingTable.getServerForPlayer(player);
            if(!serverId.equals("") && canJoin(user, serverId)) {
                Optional<RegisteredServer> opt = proxyServer.getServer(serverId);
                if(opt.isPresent()) {
                    RegisteredServer server = opt.get();
                    try {
                        if(server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
                            try {
                                server.ping().join();
                                chooseServerEvent.setInitialServer(server);
                                logger.debug("Routing player {} in {} to server {}", player.getUsername(), playerVersion.name(), server.getServerInfo().getName());
                                return;
                            } catch(CancellationException|CompletionException exception) {
                                logger.error(String.format("Unable to route %s to their last server of %s; using fallback", player.getUsername(), server.getServerInfo().getName()), exception);
                            }
                        } else {
                            logger.warn("Unable to route {} to their last server of {}: incompatible version;",
                                    player.getUsername(), server.getServerInfo().getName());
                            routingTable.removeLastServerForPlayer(player);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error(String.format("Unable to route %s to their last server of %s; using fallback", player.getUsername(), server.getServerInfo().getName()), e);
                    }
                }
            }
        } else {
            logger.warn("Missing routing table: Last server can not be loaded");
        }

        if(chooseServerEvent.getInitialServer().isPresent()) {
            RegisteredServer server = chooseServerEvent.getInitialServer().get();
            try {
                if(canJoin(user, server.getServerInfo().getName()) && server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
                    try {
                        server.ping().join();
                        chooseServerEvent.setInitialServer(server);
                        logger.info("Had no last server for {} in {}, redirecting to default of server {}", player.getUsername(), playerVersion.name(), server.getServerInfo().getName());
                        return;
                    } catch(CancellationException|CompletionException exception) {
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }

        for(RegisteredServer server : proxyServer.getAllServers()) {
            try {
                if(canJoin(user, server.getServerInfo().getName()) && server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
                    try {
                        server.ping().join();
                        chooseServerEvent.setInitialServer(server);
                        logger.info("Had no last server for {} in {}, redirecting to server {}", player.getUsername(), playerVersion.name(), server.getServerInfo().getName());
                        return;
                    } catch(CancellationException|CompletionException exception) {
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }

        logger.warn("Could not find a server for player {} in {}", player.getUsername(), playerVersion.name());
        player.disconnect(Component.text(config.noServerDisconnect));
    }

    @Subscribe
    public void onServerChange(ServerConnectedEvent serverConnectedEvent) {
        if (routingTable != null) {
            routingTable.setLast(serverConnectedEvent.getPlayer(), serverConnectedEvent.getServer());
            logger.debug("Storing last server of {} for player {} in {}",
                    serverConnectedEvent.getServer().getServerInfo().getName(),
                    serverConnectedEvent.getPlayer().getUsername(),
                    serverConnectedEvent.getPlayer().getProtocolVersion().name());
        } else {
            logger.warn("Missing routing table: Last server will not be saved");
        }
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent kickedFromServerEvent) {
        Player player = kickedFromServerEvent.getPlayer();
        ProtocolVersion playerVersion = player.getProtocolVersion();

        String kickedServerName = kickedFromServerEvent.getServer().getServerInfo().getName();
        Optional<Component> reasonOpt = kickedFromServerEvent.getServerKickReason();
        Component reasonComponent = reasonOpt.isPresent() ? reasonOpt.get() : Component.text("Unknown");

        User user = null;
        if (config.useLP) {
            try {
                LuckPerms lpApi = LuckPermsProvider.get();
                UserManager um = lpApi.getUserManager();
                user = um.loadUser(player.getUniqueId()).join();
            } catch (IllegalStateException e) {
                logger.warn("Could not load luckperms API, no permission checks will happen");
            }
        }

        boolean hadInVersion = false;
        for (RegisteredServer server : proxyServer.getAllServers()) {
            if (server == kickedFromServerEvent.getServer())
                continue;
            try {
                if (server.ping().get().getVersion().getProtocol() != playerVersion.getProtocol())
                    continue;
                hadInVersion = true;
                if(canJoin(user, server.getServerInfo().getName())) {
                    try {
                        server.ping();
                        kickedFromServerEvent.setResult(KickedFromServerEvent.RedirectPlayer.create(server));
                        logger.info("{} kicked from {} in {}, redirecting to server {}", player.getUsername(), kickedServerName, playerVersion.name(), server.getServerInfo().getName());
                        return;
                    } catch(CancellationException|CompletionException exception) {
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }

        logger.warn("Could not find a server for player {} in {} after kick from {}", player.getUsername(),
                playerVersion.name(), kickedServerName);
        // player.disconnect(Component.text(config.noServerDisconnect));
        if(!hadInVersion)
            logger.warn("- No other servers in version");

        Component disconnectMsg = Component
                .text(String.format("You were kicked from server %s because \"", kickedServerName));
        disconnectMsg.append(reasonComponent);
        if(hadInVersion)
            disconnectMsg.append(Component.text("\" and there was no other server on the network in your version you are allowed to join"));
        else
            disconnectMsg.append(Component.text("\" and there was no other server on the network in your version"));
        
        kickedFromServerEvent.setResult(KickedFromServerEvent.DisconnectPlayer.create(disconnectMsg));
    }

    public void reloadConfig() {
        Path configPath = dataDirectory.resolve("config.json");
        File f = configPath.toFile();
        if (!f.exists()) {
            logger.warn("Could not find config, creating one");
            config = new Config();
            try (FileWriter writer = new FileWriter(f)) {
                gson.toJson(config, writer);
            } catch (IOException e) {
                logger.error("Error creating config", e);
            }
            return;
        }
        try (FileReader reader = new FileReader(f)) {
            config = gson.fromJson(reader, Config.class);
            logger.info("Velocity router config loaded");
        } catch (IOException e) {
            logger.error("Error loading config", e);
        }
    }
    

}
