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
                    context.getSource().sendSuccess(() -> Component.literal("§dXPTeleport Mod v" + XptpConfig.class.getPackage().getImplementationVersion() + " by Matnepp. Standalone NeoForge 1.21.1 replacement for FTB Essentials."), false);
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
            .then(Commands.literal("help")
                .executes(AdminCommands::executeHelp)
            )
        );
    }

    private static int executeHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("§6§l=== XPTeleport Command Reference ==="));
        source.sendSystemMessage(Component.literal("§dXP Trading:"));
        source.sendSystemMessage(Component.literal("§7- §b/xpgive <player> <levels> §7(alias: §b/xpg§7) - Give XP levels"));
        source.sendSystemMessage(Component.literal("§7- §b/xprequest <player> <levels> §7(alias: §b/xpr§7) - Request XP levels"));
        source.sendSystemMessage(Component.literal("§dXP Economy Purchases:"));
        source.sendSystemMessage(Component.literal("§7- §b/buyhome §7- Purchase an extra home slot"));
        source.sendSystemMessage(Component.literal("§7- §b/buywarp <name> §7- Create/purchase a global warp"));
        source.sendSystemMessage(Component.literal("§dTeleportation:"));
        source.sendSystemMessage(Component.literal("§7- §b/tpa <player> §7/ §b/tpahere <player> §7- Teleport requests"));
        source.sendSystemMessage(Component.literal("§7- §b/tpaccept §7/ §b/tpdeny §7/ §b/tpcancel §7- Manage TPA"));
        source.sendSystemMessage(Component.literal("§7- §b/home [name] §7/ §b/sethome [name] §7/ §b/delhome [name] §7/ §b/listhomes"));
        source.sendSystemMessage(Component.literal("§7- §b/warp <name> §7/ §b/listwarps §7/ §b/delwarp <name> §7- Manage warps"));
        source.sendSystemMessage(Component.literal("§7- §b/spawn §7- Teleport to spawn"));
        source.sendSystemMessage(Component.literal("§7- §b/back §7- Return to last location"));
        source.sendSystemMessage(Component.literal("§7- §b/rtp §7(alias: §b/wild§7) - Async safe random teleport"));
        source.sendSystemMessage(Component.literal("§dAdministration:"));
        source.sendSystemMessage(Component.literal("§7- §b/xpteleport info §7- Credits"));
        source.sendSystemMessage(Component.literal("§7- §b/xpteleport reload §7(OP) - Reload configs"));
        source.sendSystemMessage(Component.literal("§7- §b/xpteleport change §7(OP) - Toggle auto-redirection"));
        source.sendSystemMessage(Component.literal("§6§l================================="));
        return 1;
    }
}
