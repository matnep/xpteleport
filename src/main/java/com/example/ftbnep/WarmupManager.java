package com.example.ftbnep;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "xpteleport", bus = EventBusSubscriber.Bus.GAME)
public class WarmupManager {
    private static final Map<UUID, WarmupTask> activeWarmups = new ConcurrentHashMap<>();

    public static void startWarmup(ServerPlayer player, TeleportLocation destination, int xpCost) {
        // Clear any existing warmup for the player
        activeWarmups.put(player.getUUID(), new WarmupTask(player, destination, xpCost));
        player.sendSystemMessage(Component.literal("§dTeleportation initiated..."), false);
    }

    public static void cancelWarmup(ServerPlayer player, String reason) {
        if (activeWarmups.containsKey(player.getUUID())) {
            activeWarmups.remove(player.getUUID());
            // Send cancellation to chat
            player.sendSystemMessage(Component.literal("§cTeleportation cancelled: " + reason), false);
            // Clear action bar
            player.sendSystemMessage(Component.literal("§cTeleport cancelled!"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        long warmupMs = FtbNepConfig.getWarmupSeconds() * 1000L;

        for (Map.Entry<UUID, WarmupTask> entry : activeWarmups.entrySet()) {
            WarmupTask task = entry.getValue();
            ServerPlayer player = task.player;

            // Check if player is offline
            if (player.isRemoved() || player.server.getPlayerList().getPlayer(player.getUUID()) == null) {
                activeWarmups.remove(entry.getKey());
                continue;
            }

            // Check movement
            BlockPos currentPos = player.blockPosition();
            if (!currentPos.equals(task.startingPos)) {
                cancelWarmup(player, "You moved!");
                continue;
            }

            long elapsed = now - task.startTime;
            long remainingSeconds = Math.max(1, (warmupMs - elapsed + 999) / 1000);

            // Check if warmup completed
            if (elapsed >= warmupMs) {
                // Verify XP one final time
                if (player.experienceLevel < task.xpCost) {
                    cancelWarmup(player, "You no longer have enough XP levels!");
                    continue;
                }

                // Verify cooldown
                if (!CooldownManager.checkCooldown(player)) {
                    activeWarmups.remove(entry.getKey());
                    continue;
                }

                // Perform teleport
                BackManager.record(player);
                task.destination.teleport(player);

                // Deduct XP
                if (task.xpCost > 0) {
                    player.giveExperienceLevels(-task.xpCost);
                    player.sendSystemMessage(Component.literal(
                        String.format(FtbNepConfig.getXpDeductedMessage(), task.xpCost)
                    ), false);
                }

                // Clear action bar and notify success
                player.sendSystemMessage(Component.literal("§aTeleported!"), true);

                // Start cooldown
                CooldownManager.startCooldown(player);
                activeWarmups.remove(entry.getKey());
            } else {
                // Update countdown on player action bar
                player.sendSystemMessage(Component.literal(
                    String.format("§eTeleporting in §6%s§e seconds. Stand still.", remainingSeconds)
                ), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancelWarmup(player, "You took damage!");
        }
    }

    private static class WarmupTask {
        public final ServerPlayer player;
        public final BlockPos startingPos;
        public final TeleportLocation destination;
        public final int xpCost;
        public final long startTime;

        public WarmupTask(ServerPlayer player, TeleportLocation destination, int xpCost) {
            this.player = player;
            this.startingPos = player.blockPosition();
            this.destination = destination;
            this.xpCost = xpCost;
            this.startTime = System.currentTimeMillis();
        }
    }
}
