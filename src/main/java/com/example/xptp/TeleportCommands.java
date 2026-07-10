package com.example.xptp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportCommands {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();

    // Cache for pending Xaero Map teleports
    private static final Map<UUID, PendingXaeroTeleport> pendingXaeroTeleports = new ConcurrentHashMap<>();
    
    // Set of active RTP searches to prevent spamming
    private static final Set<UUID> activeRtpSearches = ConcurrentHashMap.newKeySet();

    private record PendingXaeroTeleport(TeleportLocation destination, int cost, long timestamp) {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 1. spawn
        dispatcher.register(Commands.literal("spawn")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (!CooldownManager.checkCooldown(player, "spawn")) return 0;

                ServerLevel level = player.server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                TeleportLocation spawnLoc = new TeleportLocation(spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5, player.getYRot(), player.getXRot(), level.dimension().location().toString());

                int cost = Xptp.calculateXpCost(player, spawnLoc, "spawn", false);
                if (player.experienceLevel < cost) {
                    context.getSource().sendFailure(Component.literal(
                        String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
                    ));
                    return 0;
                }

                Xptp.performTeleport(player, player, spawnLoc, cost, "spawn");
                return 1;
            })
        );

        // 2. back
        dispatcher.register(Commands.literal("back")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                BackManager.BackRecord rec = BackManager.getLastRecord(player);
                if (rec == null) {
                    context.getSource().sendFailure(Component.literal("§cNo teleport/death history found."));
                    return 0;
                }

                String cmd = rec.isDeath ? "death_back" : "back";
                if (!CooldownManager.checkCooldown(player, cmd)) return 0;

                TeleportLocation loc = rec.location;
                int cost = Xptp.calculateXpCost(player, loc, cmd, false);
                if (player.experienceLevel < cost) {
                    context.getSource().sendFailure(Component.literal(
                        String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
                    ));
                    return 0;
                }

                Xptp.performTeleport(player, player, loc, cost, cmd);
                return 1;
            })
        );

        // 3. rtp / wild
        dispatcher.register(Commands.literal("rtp")
            .requires(source -> source.isPlayer())
            .executes(TeleportCommands::executeRtp)
        );
        dispatcher.register(Commands.literal("wild")
            .requires(source -> source.isPlayer())
            .executes(TeleportCommands::executeRtp)
        );

        // 4. xtp
        dispatcher.register(Commands.literal("xtp")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("confirm")
                .executes(TeleportCommands::executeConfirmTp)
            )
            .then(Commands.argument("pos", Vec3Argument.vec3())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Vec3 pos = Vec3Argument.getVec3(context, "pos");
                    return initXaeroTp(player, pos, null, null);
                })
            )
        );
    }

    private static int executeRtp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!CooldownManager.checkCooldown(player, "rtp")) return 0;

        int cost = XptpConfig.getFlatCost("rtp");
        if (player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal(
                String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        if (activeRtpSearches.contains(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("§cYou are already searching for a safe location!"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        int rtpMin = XptpConfig.getRtpMinRange();
        int rtpMax = XptpConfig.getRtpMaxRange();
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();

        player.sendSystemMessage(Component.literal("§6Looking for safe location..."));
        activeRtpSearches.add(player.getUUID());

        findRandomSafeLocationAsync(player, level, spawn, rtpMin, rtpMax, border, cost, 0);
        return 1;
    }

    private static void findRandomSafeLocationAsync(ServerPlayer player, ServerLevel level, BlockPos spawn, int rtpMin, int rtpMax, net.minecraft.world.level.border.WorldBorder border, int cost, int attempt) {
        if (!player.isAlive() || player.hasDisconnected()) {
            activeRtpSearches.remove(player.getUUID());
            return;
        }

        double distance = rtpMin + RANDOM.nextDouble() * (rtpMax - rtpMin);
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double rx = spawn.getX() + Math.cos(angle) * distance;
        double rz = spawn.getZ() + Math.sin(angle) * distance;

        final double finalRx = Math.max(border.getMinX() + 16, Math.min(border.getMaxX() - 16, rx));
        final double finalRz = Math.max(border.getMinZ() + 16, Math.min(border.getMaxZ() - 16, rz));

        int chunkX = ((int) finalRx) >> 4;
        int chunkZ = ((int) finalRz) >> 4;

        level.getChunkSource().getChunkFuture(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true)
            .whenCompleteAsync((chunkResult, thrown) -> {
                boolean nextAttemptStarted = false;
                try {
                    if (thrown != null) {
                        throw new RuntimeException(thrown);
                    }

                    net.minecraft.world.level.chunk.ChunkAccess chunk = chunkResult.orElse(null);
                    if (chunk != null) {
                        int ry = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) finalRx, (int) finalRz);
                        BlockPos pos = new BlockPos((int) finalRx, ry, (int) finalRz);

                        BlockState below = level.getBlockState(pos.below());
                        BlockState inside = level.getBlockState(pos);
                        BlockState above = level.getBlockState(pos.above());

                        if (isSafeLocation(below, inside, above)) {
                            TeleportLocation dest = new TeleportLocation(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, player.getYRot(), player.getXRot(), level.dimension().location().toString());
                            
                            activeRtpSearches.remove(player.getUUID());
                            nextAttemptStarted = true;
                            
                            Xptp.performTeleport(player, player, dest, cost, "rtp");
                        } else {
                            int maxAttempts = 15;
                            if (attempt + 1 < maxAttempts) {
                                nextAttemptStarted = true;
                                findRandomSafeLocationAsync(player, level, spawn, rtpMin, rtpMax, border, cost, attempt + 1);
                            } else {
                                player.sendSystemMessage(Component.literal("§cCould not find a safe location after " + maxAttempts + " attempts! Try again."));
                            }
                        }
                    } else {
                        int maxAttempts = 15;
                        if (attempt + 1 < maxAttempts) {
                            nextAttemptStarted = true;
                            findRandomSafeLocationAsync(player, level, spawn, rtpMin, rtpMax, border, cost, attempt + 1);
                        } else {
                            player.sendSystemMessage(Component.literal("§cCould not load chunks for safe location search. Try again."));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error during asynchronous RTP search for player " + player.getScoreboardName(), e);
                    player.sendSystemMessage(Component.literal("§cAn error occurred during RTP search!"));
                } finally {
                    if (!nextAttemptStarted) {
                        activeRtpSearches.remove(player.getUUID());
                    }
                }
            }, level.getServer());
    }

    private static boolean isSafeLocation(BlockState below, BlockState inside, BlockState above) {
        if (below.isAir() || !below.getFluidState().isEmpty()) {
            return false;
        }

        net.minecraft.world.level.block.Block belowBlock = below.getBlock();
        if (belowBlock == net.minecraft.world.level.block.Blocks.LAVA || 
            belowBlock == net.minecraft.world.level.block.Blocks.WATER ||
            belowBlock == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK ||
            belowBlock == net.minecraft.world.level.block.Blocks.CACTUS ||
            belowBlock == net.minecraft.world.level.block.Blocks.FIRE ||
            belowBlock == net.minecraft.world.level.block.Blocks.SOUL_FIRE) {
            return false;
        }

        return inside.isAir() && above.isAir();
    }

    public static int initXaeroTp(ServerPlayer player, Vec3 pos, Float yaw, Float pitch) {
        if (!CooldownManager.checkCooldown(player, "xaero")) return 0;

        float y = yaw != null ? yaw : player.getYRot();
        float p = pitch != null ? pitch : player.getXRot();
        TeleportLocation dest = new TeleportLocation(pos.x, pos.y, pos.z, y, p, player.level().dimension().location().toString());

        int cost = Xptp.calculateXpCost(player, dest, "xaero", true);

        if (player.experienceLevel < cost) {
            player.sendSystemMessage(Component.literal(
                String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        pendingXaeroTeleports.put(player.getUUID(), new PendingXaeroTeleport(dest, cost, System.currentTimeMillis()));

        String prompt = String.format(XptpConfig.getConfirmPromptMessage(), 
            String.format("%.1f", pos.x), 
            String.format("%.1f", pos.y), 
            String.format("%.1f", pos.z), 
            cost
        );

        MutableComponent confirmBtn = Component.literal(" [CONFIRM] ")
            .withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xtp confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to deduct XP and teleport!")))
            );

        player.sendSystemMessage(Component.literal(prompt).append(confirmBtn));
        return 1;
    }

    private static int executeConfirmTp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID uuid = player.getUUID();
        
        PendingXaeroTeleport pending = pendingXaeroTeleports.get(uuid);
        long timeoutMs = XptpConfig.getXaeroConfirmTimeoutSeconds() * 1000L;
        if (pending == null || (System.currentTimeMillis() - pending.timestamp) > timeoutMs) {
            player.sendSystemMessage(Component.literal("§cYou do not have a pending map teleport request, or it has expired."));
            pendingXaeroTeleports.remove(uuid);
            return 0;
        }

        if (!CooldownManager.checkCooldown(player, "xaero")) {
            pendingXaeroTeleports.remove(uuid);
            return 0;
        }

        int cost = pending.cost;
        if (player.experienceLevel < cost) {
            player.sendSystemMessage(Component.literal(
                String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            pendingXaeroTeleports.remove(uuid);
            return 0;
        }

        pendingXaeroTeleports.remove(uuid);
        Xptp.performTeleport(player, player, pending.destination, cost, "xaero");
        return 1;
    }
}
