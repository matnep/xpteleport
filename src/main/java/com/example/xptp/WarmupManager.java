package com.example.xptp;

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

    public static void startWarmup(ServerPlayer player, ServerPlayer payer, TeleportLocation destination, int xpCost, String commandType) {
        WarmupTask oldTask = activeWarmups.remove(player.getUUID());
        if (oldTask != null) {
            player.sendSystemMessage(Component.literal("§cTeleportation cancelled: A new teleport request was started."), false);
            player.sendSystemMessage(Component.literal("§cTeleport cancelled!"), true);
        }
        activeWarmups.put(player.getUUID(), new WarmupTask(player, payer.getUUID(), destination, xpCost, commandType));
        player.sendSystemMessage(Component.literal("§6Teleportation initiated..."), false);
    }

    public static void cancelWarmup(ServerPlayer player, String reason) {
        WarmupTask task = activeWarmups.get(player.getUUID());
        if (task != null) {
            activeWarmups.remove(player.getUUID());
            // Send cancellation to chat
            player.sendSystemMessage(Component.literal("§cTeleportation cancelled: " + reason), false);
            // Clear action bar
            player.sendSystemMessage(Component.literal("§cTeleport cancelled!"), true);

            // Notify payer if different
            if (!player.getUUID().equals(task.payerUuid)) {
                ServerPlayer payer = player.server.getPlayerList().getPlayer(task.payerUuid);
                if (payer != null) {
                    payer.sendSystemMessage(Component.literal("§cTeleportation of " + player.getGameProfile().getName() + " cancelled: " + reason), false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        long warmupMs = XptpConfig.getWarmupSeconds() * 1000L;

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

            // Check if payer is online
            ServerPlayer payer = player.server.getPlayerList().getPlayer(task.payerUuid);
            if (payer == null) {
                cancelWarmup(player, "The payer is no longer online!");
                continue;
            }

            long elapsed = now - task.startTime;
            long remainingSeconds = Math.max(1, (warmupMs - elapsed + 999) / 1000);

            // Check if warmup completed
            if (elapsed >= warmupMs) {
                // Verify XP one final time
                if (payer.experienceLevel < task.xpCost) {
                    cancelWarmup(player, "The payer no longer has enough XP levels!");
                    continue;
                }

                // Verify cooldown
                if (!CooldownManager.checkCooldown(player, task.commandType)) {
                    activeWarmups.remove(entry.getKey());
                    continue;
                }

                // Perform teleport
                BackManager.record(player);
                task.destination.teleport(player);

                // Deduct XP
                if (task.xpCost > 0) {
                    payer.giveExperienceLevels(-task.xpCost);
                    payer.sendSystemMessage(Component.literal(
                        String.format("§aTeleportation successful! Deducted %s XP levels from your account.", task.xpCost)
                    ), false);
                    if (!player.getUUID().equals(payer.getUUID())) {
                        player.sendSystemMessage(Component.literal(
                            String.format("§aTeleported! %s paid the %s XP level cost.", payer.getGameProfile().getName(), task.xpCost)
                        ), false);
                    }
                }

                // Clear action bar and notify success
                player.sendSystemMessage(Component.literal("§aTeleported!"), true);

                // Start cooldown
                CooldownManager.startCooldown(player, task.commandType);
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
        public final UUID payerUuid;
        public final BlockPos startingPos;
        public final TeleportLocation destination;
        public final int xpCost;
        public final String commandType;
        public final long startTime;

        public WarmupTask(ServerPlayer player, UUID payerUuid, TeleportLocation destination, int xpCost, String commandType) {
            this.player = player;
            this.payerUuid = payerUuid;
            this.startingPos = player.blockPosition();
            this.destination = destination;
            this.xpCost = xpCost;
            this.commandType = commandType;
            this.startTime = System.currentTimeMillis();
        }
    }
}
