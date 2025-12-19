package com.peter.velocityrouter;

import java.util.HashMap;
import java.util.UUID;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;

public class RoutingTableFile {

    public HashMap<UUID, PlayerRoutingTable> lastServers;

    static RoutingTableFile create() {
        RoutingTableFile f = new RoutingTableFile();
        f.lastServers = new HashMap<>();
        return f;
    }

    public static class PlayerRoutingTable {
        public String username;
        public HashMap<String, String> lastServer;

        public PlayerRoutingTable(Player player) {
            username = player.getUsername();
            lastServer = new HashMap<>();
        }

        public boolean has(ProtocolVersion version) {
            return lastServer.containsKey(version.name());
        }

        public String get(ProtocolVersion version) {
            return lastServer.getOrDefault(version.name(), "");
        }

        public void set(ProtocolVersion playerVersion, String serverId) {
            lastServer.put(playerVersion.name(), serverId);
        }

        public void remove(ProtocolVersion playerVersion) {
            lastServer.remove(playerVersion.name());
        }
    }
}
