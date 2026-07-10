package com.example.xptp;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "xpteleport", bus = EventBusSubscriber.Bus.GAME)
public class BackManager {
    public static class BackRecord {
        public final TeleportLocation location;
        public final boolean isDeath;

        public BackRecord(TeleportLocation location, boolean isDeath) {
            this.location = location;
            this.isDeath = isDeath;
        }
    }

    private static final Map<UUID, BackRecord> lastLocations = new ConcurrentHashMap<>();

    public static void record(ServerPlayer player) {
        lastLocations.put(player.getUUID(), new BackRecord(TeleportLocation.of(player), false));
    }

    public static void recordDeath(ServerPlayer player) {
        lastLocations.put(player.getUUID(), new BackRecord(TeleportLocation.of(player), true));
    }

    public static BackRecord getLastRecord(ServerPlayer player) {
        return lastLocations.get(player.getUUID());
    }

    public static TeleportLocation getLastLocation(ServerPlayer player) {
        BackRecord record = lastLocations.get(player.getUUID());
        return record != null ? record.location : null;
    }

    public static void clearLastLocation(ServerPlayer player) {
        lastLocations.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordDeath(player);
        }
    }
}
