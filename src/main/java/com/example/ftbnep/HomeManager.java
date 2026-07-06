package com.example.ftbnep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public static Map<String, TeleportLocation> getHomes(MinecraftServer server, UUID uuid) {
        Path file = getHomesDir(server).resolve(uuid.toString() + ".json");
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try (FileReader reader = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {
            Map<String, TeleportLocation> map = GSON.fromJson(reader, MAP_TYPE);
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static void saveHomes(MinecraftServer server, UUID uuid, Map<String, TeleportLocation> homes) {
        Path file = getHomesDir(server).resolve(uuid.toString() + ".json");
        try (FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
            GSON.toJson(homes, MAP_TYPE, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void LOGGER_ERROR(Exception e) {
        org.apache.logging.log4j.LogManager.getLogger().error("Failed to create homes directory", e);
    }
}
