package com.example.xptp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private static final Map<UUID, Map<String, Long>> lastTeleports = new ConcurrentHashMap<>();

    public static boolean checkCooldown(ServerPlayer player, String command) {
        if (player.hasPermissions(2)) return true; // Admins bypass cooldown

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = lastTeleports.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        long last = playerCooldowns.getOrDefault(command.toLowerCase(), 0L);
        long diff = now - last;
        long cooldownMs = XptpConfig.getCooldownSeconds(command) * 1000L;

        if (diff < cooldownMs) {
            long remaining = (cooldownMs - diff + 999L) / 1000L;
            player.sendSystemMessage(Component.literal(
                String.format("§cYou cannot use this command yet! Cooldown remaining: %s seconds.", remaining)
            ));
            return false;
        }
        return true;
    }

    public static void startCooldown(ServerPlayer player, String command) {
        UUID uuid = player.getUUID();
        lastTeleports.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(command.toLowerCase(), System.currentTimeMillis());
    }

    public static void clearCooldown(ServerPlayer player, String command) {
        UUID uuid = player.getUUID();
        Map<String, Long> playerCooldowns = lastTeleports.get(uuid);
        if (playerCooldowns != null) {
            playerCooldowns.remove(command.toLowerCase());
        }
    }
}
