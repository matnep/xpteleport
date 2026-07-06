package com.example.ftbnep;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "xpteleport", bus = EventBusSubscriber.Bus.GAME)
public class BackManager {
    private static final Map<UUID, TeleportLocation> lastLocations = new ConcurrentHashMap<>();

    public static void record(ServerPlayer player) {
        lastLocations.put(player.getUUID(), TeleportLocation.of(player));
    }

    public static TeleportLocation getLastLocation(ServerPlayer player) {
        return lastLocations.get(player.getUUID());
    }

    public static void clearLastLocation(ServerPlayer player) {
        lastLocations.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            record(player);
        }
    }
}
