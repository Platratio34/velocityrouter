package com.peter.velocityrouter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;

@Plugin(id = "velocityrouter", name = "Velocity Router", version = "1.0-SNAPSHOT", authors = {"Platratio34"})
public class VelocityRouter {

    public final ProxyServer proxyServer;
    public final Logger logger;
    public final Path dataDirectory;

    protected RoutingTable routingTable;

    @Inject
    public VelocityRouter(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        logger.info("Loading Velocity Router");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Using file-based routing table for Velocity Router");
        routingTable = new FileRoutingTable(dataDirectory.resolve("routing.json"), logger);
    }

    @Subscribe
    public void onServerChooseEvent(PlayerChooseInitialServerEvent chooseServerEvent) {
        Player player = chooseServerEvent.getPlayer();
        ProtocolVersion playerVersion = player.getProtocolVersion();

        if(routingTable != null) { 
            String serverId = routingTable.getServerForPlayer(player);
            if(!serverId.equals("")) {
                Optional<RegisteredServer> opt = proxyServer.getServer(serverId);
                if(opt.isPresent()) {
                    RegisteredServer server = opt.get();
                    try {
                        if(server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
                            try {
                                server.ping().join();
                                chooseServerEvent.setInitialServer(server);
                                logger.debug("Routig player {} in {} to server {}", player.getUsername(), playerVersion.name(), server.getServerInfo().getName());
                                return;
                            } catch(CancellationException|CompletionException exception) {
                                logger.error(String.format("Unable to route %s to their last server of %s; using fallback", player.getUsername(), server.getServerInfo().getName()), exception);
                            }
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
                if(server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
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
                if(server.ping().get().getVersion().getProtocol() == playerVersion.getProtocol()) {
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
        player.disconnect(Component.text("There are no servers on this network that you can connect to in your current Minecraft version."));
    }

    @Subscribe
    public void onServerChange(ServerConnectedEvent serverConnectedEvent) {
        if(routingTable != null) {
            routingTable.setLast(serverConnectedEvent.getPlayer(), serverConnectedEvent.getServer());
            logger.debug("Storing last server of {} for player {} in {}", serverConnectedEvent.getServer().getServerInfo().getName(), serverConnectedEvent.getPlayer().getUsername(), serverConnectedEvent.getPlayer().getProtocolVersion().name());
        } else {
            logger.warn("Missing routing table: Last server will not be saved");
        }
    }
}
