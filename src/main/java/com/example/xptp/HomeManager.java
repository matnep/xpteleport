package com.example.xptp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, TeleportLocation>>() {}.getType();

    private static Path getHomesDir(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("xpteleport").resolve("homes");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                LOGGER.error("Failed to create homes directory", e);
            }
        }
        return path;
    }

    public static PlayerHomeData getHomesData(MinecraftServer server, UUID uuid) {
        Path file = getHomesDir(server).resolve(uuid.toString() + ".json");
        if (!Files.exists(file)) {
            return new PlayerHomeData();
        }
        try (FileReader reader = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("homes") && json.has("extraHomeSlots")) {
                return GSON.fromJson(json, PlayerHomeData.class);
            } else {
                // Migrate legacy Map<String, TeleportLocation>
                Map<String, TeleportLocation> map = GSON.fromJson(json, MAP_TYPE);
                return new PlayerHomeData(map != null ? map : new HashMap<>(), 0);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load player homes for " + uuid, e);
            return new PlayerHomeData();
        }
    }

    public static void saveHomesData(MinecraftServer server, UUID uuid, PlayerHomeData data) {
        Path targetPath = getHomesDir(server).resolve(uuid.toString() + ".json");
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        try {
            try (java.io.FileWriter writer = new java.io.FileWriter(tempPath.toFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, PlayerHomeData.class, writer);
            }
            try {
                Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save player homes for " + uuid, e);
        }
    }

    public static Map<String, TeleportLocation> getHomes(MinecraftServer server, UUID uuid) {
        return getHomesData(server, uuid).getHomes();
    }

    public static void saveHomes(MinecraftServer server, UUID uuid, Map<String, TeleportLocation> homes) {
        PlayerHomeData data = getHomesData(server, uuid);
        data.setHomes(homes);
        saveHomesData(server, uuid, data);
    }
}
