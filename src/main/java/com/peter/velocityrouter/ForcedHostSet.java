package com.peter.velocityrouter;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ForcedHostSet {

    public HashMap<String, String> hosts = new HashMap<>();

    public boolean has(String host) {
        return hosts.containsKey(host);
    }

    public String get(String host) {
        return hosts.get(host);
    }

    public static class ForcedHostSetSerializer implements JsonSerializer<ForcedHostSet> {

        @Override
        public JsonElement serialize(ForcedHostSet src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            for (Entry<String, String> entry : src.hosts.entrySet()) {
                // JsonArray arr = new JsonArray();
                // for (String host : entry.getValue()) {
                //     arr.add(host);
                // }
                obj.addProperty(entry.getKey(), entry.getValue());
            }
            return obj;
        }
        
    }

    public static class ForcedHostSetDeserializer implements JsonDeserializer<ForcedHostSet> {

        @Override
        public ForcedHostSet deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            ForcedHostSet set = new ForcedHostSet();
            JsonObject obj = json.getAsJsonObject();
            for (Entry<String, JsonElement> entry : obj.entrySet()) {
                set.hosts.put(entry.getKey(), entry.getValue().getAsString());
                // HashSet<String> servers = new HashSet<>();
                // set.hosts.put(entry.getKey(), servers);
                // for (JsonElement el : entry.getValue().getAsJsonArray()) {
                //     servers.add(el.getAsString());
                // }
            }
            return set;
        }

        
    }
}
