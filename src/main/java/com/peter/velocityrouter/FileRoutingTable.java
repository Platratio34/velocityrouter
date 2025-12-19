package com.peter.velocityrouter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.peter.velocityrouter.RoutingTableFile.PlayerRoutingTable;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public class FileRoutingTable implements RoutingTable {

    protected final Path path;
    protected final Logger logger;

    public FileRoutingTable(Path path, Logger logger) {
        this.path = path;
        this.logger = logger;
    }

    @Override
    public String getServerForPlayer(Player player) {
        ProtocolVersion playerVersion = player.getProtocolVersion();
        UUID playerId = player.getUniqueId();

        Gson gson = new Gson();
        File f = path.toFile();
        if (!f.exists())
            return "";
        try (FileReader reader = new FileReader(f)) {
            RoutingTableFile data = gson.fromJson(reader, RoutingTableFile.class);
            if(!data.lastServers.containsKey(playerId))
                return "";
            PlayerRoutingTable playerTable = data.lastServers.get(playerId);
            return playerTable.get(playerVersion);
        } catch (IOException e) {
            logger.error("Error loading routing table for load", e);
        }
        return "";
    }

    @Override
    public void setLast(Player player, RegisteredServer server) {
        ProtocolVersion playerVersion = player.getProtocolVersion();
        String serverId = server.getServerInfo().getName();
        UUID playerId = player.getUniqueId();

        Gson gson = new Gson();
        RoutingTableFile data;
        File f = path.toFile();
        if (f.exists()) {
            try (FileReader reader = new FileReader(f)) {
                data = gson.fromJson(reader, RoutingTableFile.class);
            } catch (IOException e) {
                logger.error("Error loading routing table for save", e);
                return;
            }
        } else {
            data = RoutingTableFile.create();
        }
        
        if(!data.lastServers.containsKey(playerId)) {
            data.lastServers.put(playerId, new PlayerRoutingTable(player));
        }
        PlayerRoutingTable prt = data.lastServers.get(playerId);
        prt.set(playerVersion, serverId);
        prt.username = player.getUsername();

        try (FileWriter writer = new FileWriter(f)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            logger.error("Error saving routing table", e);
        }
    }

    @Override
    public void removeLastServerForPlayer(Player player) {
        ProtocolVersion playerVersion = player.getProtocolVersion();
        UUID playerId = player.getUniqueId();

        Gson gson = new Gson();
        RoutingTableFile data;
        File f = path.toFile();
        if (f.exists()) {
            try (FileReader reader = new FileReader(f)) {
                data = gson.fromJson(reader, RoutingTableFile.class);
            } catch (IOException e) {
                logger.error("Error loading routing table for save", e);
                return;
            }
        } else {
            data = RoutingTableFile.create();
        }
        
        if(!data.lastServers.containsKey(playerId)) {
            data.lastServers.put(playerId, new PlayerRoutingTable(player));
        }
        PlayerRoutingTable prt = data.lastServers.get(playerId);
        prt.remove(playerVersion);
        prt.username = player.getUsername();

        try (FileWriter writer = new FileWriter(f)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            logger.error("Error saving routing table", e);
        }
    }

}
