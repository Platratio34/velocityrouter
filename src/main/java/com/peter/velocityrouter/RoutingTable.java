package com.peter.velocityrouter;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public interface RoutingTable {

    public String getServerForPlayer(Player player);
    
    public void setLast(Player player, RegisteredServer server);

    public void removeLastServerForPlayer(Player player);
}
