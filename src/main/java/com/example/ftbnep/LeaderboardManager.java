package com.example.ftbnep;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class LeaderboardManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public record LeaderboardEntry(UUID uuid, String name, int levels, int totalXp, boolean isOp) {}
    
    private static List<LeaderboardEntry> cachedLeaderboard = new ArrayList<>();
    private static long lastUpdateTime = 0;
    private static final AtomicBoolean isUpdating = new AtomicBoolean(false);

    public static List<LeaderboardEntry> getLeaderboard(MinecraftServer server) {
        long now = System.currentTimeMillis();
        // Refresh every 5 minutes in background
        if (now - lastUpdateTime > 300000 && isUpdating.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    updateLeaderboard(server);
                } catch (Exception e) {
                    LOGGER.error("Failed to update XP leaderboard", e);
                } finally {
                    isUpdating.set(false);
                }
            });
        }
        return cachedLeaderboard;
    }

    public static void forceUpdate(MinecraftServer server) {
        if (isUpdating.compareAndSet(false, true)) {
            try {
                updateLeaderboard(server);
            } catch (Exception e) {
                LOGGER.error("Failed to force update XP leaderboard", e);
            } finally {
                isUpdating.set(false);
            }
        }
    }

    private static void updateLeaderboard(MinecraftServer server) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        Set<UUID> processed = new HashSet<>();

        // 1. Process online players (live XP)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            String name = player.getGameProfile().getName();
            int levels = player.experienceLevel;
            int totalXp = player.totalExperience;
            boolean isOp = server.getPlayerList().isOp(player.getGameProfile());
            entries.add(new LeaderboardEntry(uuid, name, levels, totalXp, isOp));
            processed.add(uuid);
        }

        // 2. Process offline players from playerdata files
        File playerdataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        if (playerdataDir.exists() && playerdataDir.isDirectory()) {
            File[] files = playerdataDir.listFiles((dir, name) -> name.endsWith(".nbt"));
            if (files != null) {
                for (File file : files) {
                    try {
                        String filename = file.getName();
                        String uuidStr = filename.substring(0, filename.length() - 4);
                        UUID uuid = UUID.fromString(uuidStr);

                        if (processed.contains(uuid)) {
                            continue; // Skip online players
                        }

                        CompoundTag tag;
                        try (InputStream stream = Files.newInputStream(file.toPath())) {
                            tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                        }

                        int xpLevel = tag.getInt("XpLevel");
                        int xpTotal = tag.getInt("XpTotal");

                        // Resolve name
                        String name = "Unknown Player";
                        Optional<GameProfile> profile = server.getProfileCache().get(uuid);
                        if (profile.isPresent()) {
                            name = profile.get().getName();
                        }

                        boolean isOp = server.getPlayerList().isOp(new GameProfile(uuid, null));
                        entries.add(new LeaderboardEntry(uuid, name, xpLevel, xpTotal, isOp));
                    } catch (Exception e) {
                        // Skip corrupted/invalid files
                    }
                }
            }
        }

        // Filter based on OP settings
        boolean showOps = FtbNepConfig.isShowOpsOnLeaderboard();
        List<LeaderboardEntry> filtered = new ArrayList<>();
        for (LeaderboardEntry entry : entries) {
            if (entry.isOp() && !showOps) {
                continue;
            }
            filtered.add(entry);
        }

        // Sort by total XP descending, then levels, then name
        filtered.sort((a, b) -> {
            int cmp = Integer.compare(b.totalXp(), a.totalXp());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.levels(), a.levels());
            if (cmp != 0) return cmp;
            return a.name().compareToIgnoreCase(b.name());
        });

        // Keep top 10
        if (filtered.size() > 10) {
            cachedLeaderboard = new ArrayList<>(filtered.subList(0, 10));
        } else {
            cachedLeaderboard = new ArrayList<>(filtered);
        }
        lastUpdateTime = System.currentTimeMillis();
    }
}
