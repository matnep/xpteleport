package com.example.ftbnep;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private static final Map<UUID, Long> lastTeleports = new ConcurrentHashMap<>();

    public static boolean checkCooldown(ServerPlayer player) {
        if (player.hasPermissions(2)) return true; // Admins bypass cooldown

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        long last = lastTeleports.getOrDefault(uuid, 0L);
        long diff = now - last;
        long cooldownMs = FtbNepConfig.getCooldownSeconds() * 1000L;

        if (diff < cooldownMs) {
            long remaining = (cooldownMs - diff) / 1000L;
            player.sendSystemMessage(Component.literal(
                String.format("§cYou cannot teleport yet! Cooldown remaining: %s seconds.", remaining)
            ));
            return false;
        }
        return true;
    }

    public static void startCooldown(ServerPlayer player) {
        lastTeleports.put(player.getUUID(), System.currentTimeMillis());
    }

    public static void clearCooldown(ServerPlayer player) {
        lastTeleports.remove(player.getUUID());
    }
}
