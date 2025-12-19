package com.peter.velocityrouter;

public class Config {

    public boolean useLP = true;

    public String joinPermission = "robobouncer.join";

    public String noServerDisconnect = "There are no servers on this network that you are allowed to connect to in your current Minecraft version.";
    public String noPermDisconnect = "There are no servers on this network that you are allowed to connect to.";

    public ForcedHostSet forcedHostSet = new ForcedHostSet();
}
