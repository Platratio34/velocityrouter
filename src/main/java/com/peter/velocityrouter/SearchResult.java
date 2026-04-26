package com.peter.velocityrouter;

import com.velocitypowered.api.proxy.server.ServerPing.Version;

public class SearchResult {


    public Version version;
    public int numInVersion = 0;
    public int numTotal = 0;
    public int numSkipped = 0;
    public int numErrored = 0;

    public void add(Version version) {
        if (this.version == version) {
            numInVersion++;
        }
        numTotal++;
    }

    public void skip() {
        numSkipped++;
    }
    public void error() {
        numErrored++;
    }

    public String out() {
        String str = String
                .format("Found %d server(s) in version %s; %d total server(s) checked; Skipped %d server(s) and encountered %s server(s) errored", numInVersion, version.toString(), numTotal, numSkipped, numErrored);
        return str;
    }
}
