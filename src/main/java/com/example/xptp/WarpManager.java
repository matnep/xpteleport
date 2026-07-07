package com.example.xptp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final Type MAP_TYPE = new TypeToken<Map<String, WarpInfo>>() {}.getType();

    private static Map<String, WarpInfo> warps = new HashMap<>();

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
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, WarpInfo> parsedWarps = new HashMap<>();
                for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                    String warpName = entry.getKey().toLowerCase();
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    if (obj.has("location")) {
                        WarpInfo warpInfo = GSON.fromJson(obj, WarpInfo.class);
                        parsedWarps.put(warpName, warpInfo);
                    } else {
                        // Migrate legacy TeleportLocation directly
                        TeleportLocation loc = GSON.fromJson(obj, TeleportLocation.class);
                        parsedWarps.put(warpName, new WarpInfo(loc, null));
                    }
                }
                warps = parsedWarps;
            }
            LOGGER.info("Xptp warps loaded successfully. Total warps: {}", warps.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load Xptp warps", e);
        }
    }

    public static void save() {
        try {
            java.nio.file.Path targetPath = WARP_FILE.toPath();
            java.nio.file.Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
            java.io.File tempFile = tempPath.toFile();

            java.io.File parent = tempFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (java.io.FileWriter writer = new java.io.FileWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(warps, MAP_TYPE, writer);
            }

            try {
                java.nio.file.Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save Xptp warps", e);
        }
    }

    public static Map<String, WarpInfo> getWarps() {
        return warps;
    }

    public static TeleportLocation getWarp(String name) {
        WarpInfo info = warps.get(name.toLowerCase());
        return info != null ? info.getLocation() : null;
    }

    public static WarpInfo getWarpInfo(String name) {
        return warps.get(name.toLowerCase());
    }

    public static void addWarp(String name, TeleportLocation loc) {
        addWarp(name, loc, null);
    }

    public static void addWarp(String name, TeleportLocation loc, String creatorUuid) {
        warps.put(name.toLowerCase(), new WarpInfo(loc, creatorUuid));
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
