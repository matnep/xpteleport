package com.example.xptp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class TpaCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    ServerPlayer player = context.getSource().getPlayerOrException();

                    if (target.getUUID().equals(player.getUUID())) {
                        context.getSource().sendFailure(Component.literal("§cYou cannot teleport to yourself!"));
                        return 0;
                    }

                    TpaManager.addRequest(target.getUUID(), player.getUUID(), false);
                    
                    player.sendSystemMessage(Component.literal("§aTPA request sent to " + target.getGameProfile().getName()));
                    
                    target.sendSystemMessage(Component.literal("§a" + player.getGameProfile().getName() + " wants to teleport to you."));
                    
                    MutableComponent prompt = Component.literal("§aUse §e/tpaccept§a or §c/tpdeny§a to respond. Or click: ")
                        .append(Component.literal("[ACCEPT]")
                            .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept request")))))
                        .append(" | ")
                        .append(Component.literal("[DENY]")
                            .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to deny request")))));
                    target.sendSystemMessage(prompt);
                    return 1;
                })
            )
        );

        dispatcher.register(Commands.literal("tpahere")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    ServerPlayer player = context.getSource().getPlayerOrException();

                    if (target.getUUID().equals(player.getUUID())) {
                        context.getSource().sendFailure(Component.literal("§cYou cannot teleport yourself to yourself!"));
                        return 0;
                    }

                    TpaManager.addRequest(target.getUUID(), player.getUUID(), true);
                    
                    player.sendSystemMessage(Component.literal("§aTPAHere request sent to " + target.getGameProfile().getName()));
                    
                    target.sendSystemMessage(Component.literal("§a" + player.getGameProfile().getName() + " wants you to teleport to them."));
                    
                    MutableComponent prompt = Component.literal("§aUse §e/tpaccept§a or §c/tpdeny§a to respond. Or click: ")
                        .append(Component.literal("[ACCEPT]")
                            .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept request")))))
                        .append(" | ")
                        .append(Component.literal("[DENY]")
                            .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to deny request")))));
                    target.sendSystemMessage(prompt);
                    return 1;
                })
            )
        );

        dispatcher.register(Commands.literal("tpaccept")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer target = context.getSource().getPlayerOrException();
                TpaManager.TPARequest req = TpaManager.getRequest(target.getUUID());
                if (req == null) {
                    context.getSource().sendFailure(Component.literal("§cYou do not have a pending teleport request."));
                    return 0;
                }

                ServerPlayer requester = target.server.getPlayerList().getPlayer(req.requester());
                if (requester == null) {
                    context.getSource().sendFailure(Component.literal("§cThe requester has gone offline."));
                    TpaManager.removeRequest(target.getUUID());
                    return 0;
                }

                if (req.here()) {
                    // target teleports to requester. Requester pays cost.
                    if (!CooldownManager.checkCooldown(target, "tpahere")) return 0;
                    TeleportLocation reqLoc = TeleportLocation.of(requester);
                    int cost = Xptp.calculateXpCost(requester, reqLoc, "tpahere", false);

                    if (requester.experienceLevel < cost) {
                        context.getSource().sendFailure(Component.literal(
                            String.format("§cRequester %s no longer has enough XP levels! (Cost: %s levels)", requester.getGameProfile().getName(), cost)
                        ));
                        return 0;
                    }
                    
                    requester.sendSystemMessage(Component.literal(
                        String.format("§a%s accepted your request. Teleporting them to you in 3 seconds (Cost: %s XP levels)...", target.getGameProfile().getName(), cost)
                    ));
                    
                    TpaManager.removeRequest(target.getUUID());
                    Xptp.performTeleport(target, requester, reqLoc, cost, "tpahere");
                } else {
                    // requester teleports to target. Requester pays cost.
                    if (!CooldownManager.checkCooldown(requester, "tpa")) {
                        context.getSource().sendFailure(Component.literal("§cRequester is on a teleport cooldown."));
                        return 0;
                    }
                    TeleportLocation targetLoc = TeleportLocation.of(target);
                    int cost = Xptp.calculateXpCost(requester, targetLoc, "tpa", false);

                    if (requester.experienceLevel < cost) {
                        context.getSource().sendFailure(Component.literal(
                            "§cRequester " + requester.getGameProfile().getName() + " no longer has enough XP levels to teleport."
                        ));
                        return 0;
                    }

                    TpaManager.removeRequest(target.getUUID());
                    Xptp.performTeleport(requester, requester, targetLoc, cost, "tpa");
                    target.sendSystemMessage(Component.literal("§aTPA request accepted. Teleporting..."));
                }
                return 1;
            })
        );

        dispatcher.register(Commands.literal("tpdeny")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer target = context.getSource().getPlayerOrException();
                TpaManager.TPARequest req = TpaManager.getRequest(target.getUUID());
                if (req == null) {
                    context.getSource().sendFailure(Component.literal("§cYou do not have a pending teleport request."));
                    return 0;
                }

                TpaManager.removeRequest(target.getUUID());
                target.sendSystemMessage(Component.literal("§aTPA request denied."));

                ServerPlayer requester = target.server.getPlayerList().getPlayer(req.requester());
                if (requester != null) {
                    requester.sendSystemMessage(Component.literal("§c" + target.getGameProfile().getName() + " denied your TPA request."));
                }
                return 1;
            })
        );

        dispatcher.register(Commands.literal("tpcancel")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                // Find any request sent by player
                boolean found = false;
                for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                    TpaManager.TPARequest req = TpaManager.getRequest(other.getUUID());
                    if (req != null && req.requester().equals(player.getUUID())) {
                        TpaManager.removeRequest(other.getUUID());
                        player.sendSystemMessage(Component.literal("§aCancelled pending TPA request to " + other.getGameProfile().getName()));
                        other.sendSystemMessage(Component.literal("§cTPA request from " + player.getGameProfile().getName() + " was cancelled."));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    context.getSource().sendFailure(Component.literal("§cYou do not have any pending TPA requests."));
                    return 0;
                }
                return 1;
            })
        );
    }
}
