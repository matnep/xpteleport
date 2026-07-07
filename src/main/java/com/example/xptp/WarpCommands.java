package com.example.xptp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class WarpCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warp")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> executeWarp(context, StringArgumentType.getString(context, "name")))
            )
        );

        dispatcher.register(Commands.literal("setwarp")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(context, "name");
                    if (!Xptp.isValidName(name)) {
                        context.getSource().sendFailure(Component.literal("§cInvalid name! Use only alphanumeric characters and underscores, max 32 characters."));
                        return 0;
                    }
                    WarpManager.addWarp(name, TeleportLocation.of(player));
                    player.sendSystemMessage(Component.literal("§aWarp '" + name + "' set successfully!"));
                    return 1;
                })
            )
        );

        dispatcher.register(Commands.literal("delwarp")
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    WarpInfo info = WarpManager.getWarpInfo(name);
                    if (info == null) {
                        context.getSource().sendFailure(Component.literal("§cWarp '" + name + "' not found."));
                        return 0;
                    }

                    boolean canDelete = false;
                    try {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (player.hasPermissions(2) || (info.getCreatorUuid() != null && info.getCreatorUuid().equals(player.getUUID().toString()))) {
                            canDelete = true;
                        }
                    } catch (CommandSyntaxException e) {
                        canDelete = true;
                    }

                    if (!canDelete) {
                        context.getSource().sendFailure(Component.literal("§cYou do not own this warp!"));
                        return 0;
                    }

                    if (WarpManager.removeWarp(name)) {
                        context.getSource().sendSuccess(() -> Component.literal("§aWarp '" + name + "' deleted successfully!"), true);
                        return 1;
                    } else {
                        context.getSource().sendFailure(Component.literal("§cWarp '" + name + "' not found."));
                        return 0;
                    }
                })
            )
        );

        dispatcher.register(Commands.literal("listwarps")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Map<String, WarpInfo> warps = WarpManager.getWarps();
                if (warps.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§cNo warps set on this server."));
                } else {
                    String names = String.join(", ", warps.keySet());
                    player.sendSystemMessage(Component.literal("§aAvailable warps: " + names));
                }
                return 1;
            })
        );

        dispatcher.register(Commands.literal("buywarp")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> executeBuyWarp(context, StringArgumentType.getString(context, "name")))
            )
        );
    }

    private static int executeWarp(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        WarpInfo info = WarpManager.getWarpInfo(name);
        if (info == null) {
            context.getSource().sendFailure(Component.literal("§cWarp '" + name + "' not found."));
            return 0;
        }
        TeleportLocation loc = info.getLocation();

        if (!CooldownManager.checkCooldown(player)) return 0;

        int cost = Xptp.calculateXpCost(player, loc, false);
        if (info.getCreatorUuid() != null && info.getCreatorUuid().equals(player.getUUID().toString())) {
            cost = (int) Math.round(cost * XptpConfig.getCreatorWarpCostMultiplier());
        }

        if (player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal(
                String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        Xptp.performTeleport(player, loc, cost);
        return 1;
    }

    private static int executeBuyWarp(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!Xptp.isValidName(name)) {
            context.getSource().sendFailure(Component.literal("§cInvalid name! Use only alphanumeric characters and underscores, max 32 characters."));
            return 0;
        }

        if (WarpManager.getWarp(name) != null) {
            context.getSource().sendFailure(Component.literal("§cWarp '" + name + "' already exists!"));
            return 0;
        }

        int baseCost = XptpConfig.getBuyWarpCost();
        double globalMult = XptpConfig.getGlobalCostMultiplier();
        int cost = (int) Math.round(baseCost * globalMult);

        boolean isOp = player.hasPermissions(2);
        if (!isOp && player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal("§cYou do not have enough XP levels! (Cost: " + cost + " levels, Current: " + player.experienceLevel + " levels)"));
            return 0;
        }

        if (!isOp) {
            player.giveExperienceLevels(-cost);
        }

        WarpManager.addWarp(name, TeleportLocation.of(player), player.getUUID().toString());
        player.sendSystemMessage(Component.literal("§aSuccessfully purchased and set global warp '" + name + "'!"));
        return 1;
    }
}
