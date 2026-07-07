package com.example.xptp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.util.List;

public class XpCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 1. xpgive
        dispatcher.register(Commands.literal("xpgive")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1, 10000))
                    .executes(XpCommands::executeXpGive)
                )
            )
        );
        dispatcher.register(Commands.literal("xpg")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1, 10000))
                    .executes(XpCommands::executeXpGive)
                )
            )
        );

        // 2. xprequest
        dispatcher.register(Commands.literal("xprequest")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("accept")
                .executes(XpCommands::executeXpRequestAccept)
            )
            .then(Commands.literal("deny")
                .executes(XpCommands::executeXpRequestDeny)
            )
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1, 10000))
                    .executes(XpCommands::executeXpRequest)
                )
            )
        );
        dispatcher.register(Commands.literal("xpr")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("accept")
                .executes(XpCommands::executeXpRequestAccept)
            )
            .then(Commands.literal("deny")
                .executes(XpCommands::executeXpRequestDeny)
            )
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1, 10000))
                    .executes(XpCommands::executeXpRequest)
                )
            )
        );

        // 3. xp leaderboard
        dispatcher.register(Commands.literal("xp")
            .then(Commands.literal("leaderboard")
                .executes(XpCommands::executeLeaderboard)
            )
        );
    }

    private static int executeXpGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int levels = IntegerArgumentType.getInteger(context, "levels");

        if (player.getUUID().equals(target.getUUID())) {
            context.getSource().sendFailure(Component.literal("§cYou cannot give XP to yourself!"));
            return 0;
        }

        boolean isOp = player.hasPermissions(2);
        if (!isOp && player.experienceLevel < levels) {
            context.getSource().sendFailure(Component.literal("§cYou do not have enough XP levels to give! (Cost: " + levels + " levels, Current: " + player.experienceLevel + " levels)"));
            return 0;
        }

        if (!isOp) {
            player.giveExperienceLevels(-levels);
        }
        target.giveExperienceLevels(levels);

        player.sendSystemMessage(Component.literal("§aSuccessfully gave " + levels + " XP levels to " + target.getGameProfile().getName()));
        target.sendSystemMessage(Component.literal("§a" + player.getGameProfile().getName() + " gave you " + levels + " XP levels!"));
        return 1;
    }

    private static int executeXpRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int levels = IntegerArgumentType.getInteger(context, "levels");

        if (player.getUUID().equals(target.getUUID())) {
            context.getSource().sendFailure(Component.literal("§cYou cannot request XP from yourself!"));
            return 0;
        }

        XpRequestManager.addRequest(target.getUUID(), player.getUUID(), levels);

        player.sendSystemMessage(Component.literal("§aXP request for " + levels + " levels sent to " + target.getGameProfile().getName()));
        
        target.sendSystemMessage(Component.literal("§a" + player.getGameProfile().getName() + " is requesting " + levels + " XP levels from you."));
        
        MutableComponent prompt = Component.literal("§aClick to respond: ")
            .append(Component.literal("[ACCEPT]")
                .withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xpr accept"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept request")))))
            .append(" | ")
            .append(Component.literal("[DENY]")
                .withStyle(style -> style
                    .withColor(ChatFormatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xpr deny"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to deny request")))));
        target.sendSystemMessage(prompt);
        return 1;
    }

    private static int executeXpRequestAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        XpRequestManager.XPRequest req = XpRequestManager.getRequest(target.getUUID());
        if (req == null) {
            context.getSource().sendFailure(Component.literal("§cYou do not have a pending XP request."));
            return 0;
        }

        ServerPlayer requester = target.server.getPlayerList().getPlayer(req.requester());
        if (requester == null) {
            context.getSource().sendFailure(Component.literal("§cThe requester has gone offline."));
            XpRequestManager.removeRequest(target.getUUID());
            return 0;
        }

        int levels = req.levels();
        if (target.experienceLevel < levels) {
            context.getSource().sendFailure(Component.literal("§cYou do not have enough XP levels to accept! (Required: " + levels + " levels, Current: " + target.experienceLevel + " levels)"));
            return 0;
        }

        XpRequestManager.removeRequest(target.getUUID());
        target.giveExperienceLevels(-levels);
        requester.giveExperienceLevels(levels);

        target.sendSystemMessage(Component.literal("§aAccepted XP request. Sent " + levels + " levels to " + requester.getGameProfile().getName()));
        requester.sendSystemMessage(Component.literal("§a" + target.getGameProfile().getName() + " accepted your XP request. Received " + levels + " levels!"));
        return 1;
    }

    private static int executeXpRequestDeny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        XpRequestManager.XPRequest req = XpRequestManager.getRequest(target.getUUID());
        if (req == null) {
            context.getSource().sendFailure(Component.literal("§cYou do not have a pending XP request."));
            return 0;
        }

        XpRequestManager.removeRequest(target.getUUID());
        target.sendSystemMessage(Component.literal("§aXP request denied."));

        ServerPlayer requester = target.server.getPlayerList().getPlayer(req.requester());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal("§c" + target.getGameProfile().getName() + " denied your XP request."));
        }
        return 1;
    }

    private static int executeLeaderboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        net.minecraft.server.MinecraftServer server = source.getServer();
        List<LeaderboardManager.LeaderboardEntry> list = LeaderboardManager.getLeaderboard(server);

        if (list.isEmpty()) {
            LeaderboardManager.forceUpdate(server);
            list = LeaderboardManager.getLeaderboard(server);
        }

        source.sendSystemMessage(Component.literal("§6§l=== XP Levels Leaderboard ==="));
        if (list.isEmpty()) {
            source.sendSystemMessage(Component.literal("§7No players found on the leaderboard yet."));
        } else {
            int rank = 1;
            for (LeaderboardManager.LeaderboardEntry entry : list) {
                String color = "§e";
                if (rank == 1) color = "§a§l";
                else if (rank == 2) color = "§6";
                else if (rank == 3) color = "§7";
                
                String rankStr = String.format("%s%d. %s", color, rank, entry.name());
                source.sendSystemMessage(Component.literal(
                    String.format("%-25s §6%d levels §7(%d total XP)", rankStr, entry.levels(), entry.totalXp())
                ));
                rank++;
            }
        }
        source.sendSystemMessage(Component.literal("§6§l============================"));
        return 1;
    }
}
