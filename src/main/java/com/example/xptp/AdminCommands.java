package com.example.xptp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AdminCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xpteleport")
            .then(Commands.literal("info")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Made by Matnepp from Burhan Bistro<3"), false);
                    return 1;
                })
            )
            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    XptpConfig.load();
                    WarpManager.load();
                    context.getSource().sendSuccess(() -> Component.literal("§aXPTeleport configuration and warps reloaded successfully!"), true);
                    return 1;
                })
            )
            .then(Commands.literal("change")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    boolean nextVal = !XptpConfig.isRedirectTpToXtp();
                    XptpConfig.setRedirectTpToXtp(nextVal);
                    XptpConfig.save();
                    context.getSource().sendSuccess(() -> Component.literal("§aXPTeleport auto-redirection toggled to: " + nextVal), true);
                    return 1;
                })
            )
            .then(Commands.literal("leaderboard")
                .executes(XpCommands::executeLeaderboard)
            )
            .then(Commands.literal("help")
                .executes(AdminCommands::executeHelp)
            )
        );
    }

    private static int executeHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("§6§l=== XPTeleport Command Reference ==="));
        source.sendSystemMessage(Component.literal("§6XP Trading:"));
        source.sendSystemMessage(Component.literal("§7- §6/xpgive <player> <levels> §7(alias: §6/xpg§7) - Give XP levels"));
        source.sendSystemMessage(Component.literal("§7- §6/xprequest <player> <levels> §7(alias: §6/xpr§7) - Request XP levels"));
        source.sendSystemMessage(Component.literal("§6XP Economy Purchases:"));
        source.sendSystemMessage(Component.literal("§7- §6/buyhome §7- Purchase an extra home slot"));
        source.sendSystemMessage(Component.literal("§7- §6/buywarp <name> §7- Create/purchase a global warp"));
        source.sendSystemMessage(Component.literal("§6Teleportation:"));
        source.sendSystemMessage(Component.literal("§7- §6/tpa <player> §7/ §6/tpahere <player> §7- Teleport requests"));
        source.sendSystemMessage(Component.literal("§7- §6/tpaccept §7/ §6/tpdeny §7/ §6/tpcancel §7- Manage TPA"));
        source.sendSystemMessage(Component.literal("§7- §6/home [name] §7/ §6/sethome [name] §7/ §6/delhome [name] §7/ §6/listhomes"));
        source.sendSystemMessage(Component.literal("§7- §6/warp <name> §7/ §6/listwarps §7/ §6/delwarp <name> §7- Manage warps"));
        source.sendSystemMessage(Component.literal("§7- §6/spawn §7- Teleport to spawn"));
        source.sendSystemMessage(Component.literal("§7- §6/back §7- Return to last location"));
        source.sendSystemMessage(Component.literal("§7- §6/rtp §7(alias: §6/wild§7) - Async safe random teleport"));
        source.sendSystemMessage(Component.literal("§6Administration:"));
        source.sendSystemMessage(Component.literal("§7- §6/xpteleport info §7- Credits"));
        source.sendSystemMessage(Component.literal("§7- §6/xpteleport leaderboard §7- XP levels leaderboard"));
        source.sendSystemMessage(Component.literal("§7- §6/xpteleport reload §7(OP) - Reload configs"));
        source.sendSystemMessage(Component.literal("§7- §6/xpteleport change §7(OP) - Toggle auto-redirection"));
        source.sendSystemMessage(Component.literal("§6§l================================="));
        return 1;
    }
}
