package com.example.ftbnep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WarpManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File WARP_FILE = new File("config/xpteleport/warps.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, TeleportLocation>>() {}.getType();

    private static Map<String, TeleportLocation> warps = new HashMap<>();

    static {
        load();
    }

    public static void load() {
        try {
            File parent = WARP_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!WARP_FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(WARP_FILE, StandardCharsets.UTF_8)) {
                Map<String, TeleportLocation> map = GSON.fromJson(reader, MAP_TYPE);
                warps = map != null ? map : new HashMap<>();
            }
            LOGGER.info("FTBNep warps loaded successfully. Total warps: {}", warps.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load FTBNep warps", e);
        }
    }

    public static void save() {
        try {
            File parent = WARP_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(WARP_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(warps, MAP_TYPE, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save FTBNep warps", e);
        }
    }

    public static Map<String, TeleportLocation> getWarps() {
        return warps;
    }

    public static TeleportLocation getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public static void addWarp(String name, TeleportLocation loc) {
        warps.put(name.toLowerCase(), loc);
        save();
    }

    public static boolean removeWarp(String name) {
        if (warps.containsKey(name.toLowerCase())) {
            warps.remove(name.toLowerCase());
            save();
            return true;
        }
        return false;
    }
}
