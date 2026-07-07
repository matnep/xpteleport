package com.example.xptp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, TeleportLocation>>() {}.getType();

    private static Path getHomesDir(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("xpteleport").resolve("homes");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                LOGGER_ERROR(e);
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
            e.printStackTrace();
            return new PlayerHomeData();
        }
    }

    public static void saveHomesData(MinecraftServer server, UUID uuid, PlayerHomeData data) {
        Path file = getHomesDir(server).resolve(uuid.toString() + ".json");
        try (FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
            GSON.toJson(data, PlayerHomeData.class, writer);
        } catch (Exception e) {
            e.printStackTrace();
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

    private static void LOGGER_ERROR(Exception e) {
        org.apache.logging.log4j.LogManager.getLogger().error("Failed to create homes directory", e);
    }
}
