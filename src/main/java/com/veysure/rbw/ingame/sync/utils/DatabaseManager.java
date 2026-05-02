package com.veysure.rbw.ingame.sync.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {

    private static JavaPlugin plugin;
    private static File linksFile;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, String> linksCache;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        linksFile = new File(plugin.getDataFolder(), "links.json");

        if (!linksFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                linksFile.createNewFile();
                try (FileWriter writer = new FileWriter(linksFile)) {
                    gson.toJson(new HashMap<String, String>(), writer);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create links.json: " + e.getMessage());
            }
        }

        linksCache = loadFromDisk();
        if (linksCache == null) {
            plugin.getLogger().warning("Could not read links.json on startup — starting with empty cache.");
            linksCache = new HashMap<>();
        }

        plugin.getLogger().info("Loaded " + linksCache.size() + " account link(s) from disk.");
    }

    public static String getDiscordId(UUID playerUUID) {
        return linksCache.get(playerUUID.toString());
    }

    public static void saveLink(UUID playerUUID, String discordId) {
        linksCache.put(playerUUID.toString(), discordId);
        flushToDisk();
    }

    public static void removeLink(UUID playerUUID) {
        if (linksCache.remove(playerUUID.toString()) != null) {
            flushToDisk();
        }
    }

    private static void flushToDisk() {
        try (FileWriter writer = new FileWriter(linksFile)) {
            gson.toJson(linksCache, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write links.json: " + e.getMessage());
        }
    }

    private static Map<String, String> loadFromDisk() {
        if (!linksFile.exists()) {
            plugin.getLogger().warning("links.json is missing — was it deleted?");
            return null;
        }

        try (FileReader reader = new FileReader(linksFile)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> result = gson.fromJson(reader, type);
            return result != null ? result : new HashMap<>();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read links.json: " + e.getMessage());
            return null;
        }
    }
}
