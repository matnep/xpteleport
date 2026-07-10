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

public class HomeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("home")
            .requires(source -> source.isPlayer())
            .executes(context -> executeHome(context, "home"))
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> executeHome(context, StringArgumentType.getString(context, "name")))
            )
        );

        dispatcher.register(Commands.literal("sethome")
            .requires(source -> source.isPlayer())
            .executes(context -> executeSetHome(context, "home"))
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> executeSetHome(context, StringArgumentType.getString(context, "name")))
            )
        );

        dispatcher.register(Commands.literal("delhome")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> executeDelHome(context, StringArgumentType.getString(context, "name")))
            )
        );

        dispatcher.register(Commands.literal("listhomes")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                PlayerHomeData homeData = HomeManager.getHomesData(player.server, player.getUUID());
                Map<String, TeleportLocation> homes = homeData.getHomes();
                int maxAllowed = XptpConfig.getMaxHomes() + homeData.getExtraHomeSlots();
                if (homes.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§cYou have no homes set."));
                } else {
                    String names = String.join(", ", homes.keySet());
                    player.sendSystemMessage(Component.literal("§aYour homes (" + homes.size() + "/" + maxAllowed + "): " + names));
                }
                return 1;
            })
        );

        dispatcher.register(Commands.literal("buyhome")
            .requires(source -> source.isPlayer())
            .executes(HomeCommands::executeBuyHome)
        );
    }

    private static int executeSetHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!Xptp.isValidName(name)) {
            context.getSource().sendFailure(Component.literal("§cInvalid name! Use only alphanumeric characters and underscores, max 32 characters."));
            return 0;
        }

        PlayerHomeData homeData = HomeManager.getHomesData(player.server, player.getUUID());
        Map<String, TeleportLocation> homes = homeData.getHomes();
        int maxAllowed = XptpConfig.getMaxHomes() + homeData.getExtraHomeSlots();

        if (homes.size() >= maxAllowed && !homes.containsKey(name.toLowerCase())) {
            context.getSource().sendFailure(Component.literal(
                "§cYou cannot set any more homes! (Limit: " + maxAllowed + ")"
            ));
            return 0;
        }

        homes.put(name.toLowerCase(), TeleportLocation.of(player));
        homeData.setHomes(homes);
        HomeManager.saveHomesData(player.server, player.getUUID(), homeData);
        player.sendSystemMessage(Component.literal("§aHome '" + name + "' set successfully."));
        return 1;
    }

    private static int executeHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, TeleportLocation> homes = HomeManager.getHomes(player.server, player.getUUID());
        TeleportLocation loc = homes.get(name.toLowerCase());

        if (loc == null) {
            context.getSource().sendFailure(Component.literal("§cHome '" + name + "' not found."));
            return 0;
        }

        if (!CooldownManager.checkCooldown(player, "home")) return 0;

        int cost = Xptp.calculateXpCost(player, loc, "home", false);
        if (player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal(
                String.format(XptpConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        Xptp.performTeleport(player, player, loc, cost, "home");
        return 1;
    }

    private static int executeDelHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerHomeData homeData = HomeManager.getHomesData(player.server, player.getUUID());
        Map<String, TeleportLocation> homes = homeData.getHomes();

        if (homes.containsKey(name.toLowerCase())) {
            homes.remove(name.toLowerCase());
            homeData.setHomes(homes);
            HomeManager.saveHomesData(player.server, player.getUUID(), homeData);
            player.sendSystemMessage(Component.literal("§aHome '" + name + "' deleted."));
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cHome '" + name + "' not found."));
            return 0;
        }
    }

    private static int executeBuyHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerHomeData homeData = HomeManager.getHomesData(player.server, player.getUUID());
        int extraSlots = homeData.getExtraHomeSlots();
        int maxExtra = XptpConfig.getMaxExtraHomeSlots();

        if (extraSlots >= maxExtra) {
            context.getSource().sendFailure(Component.literal("§cYou have already purchased the maximum number of extra home slots (" + maxExtra + ")."));
            return 0;
        }

        int baseCost = XptpConfig.getBuyHomeSlotCost();
        double mult = XptpConfig.getBuyHomeSlotMultiplier();
        double globalMult = XptpConfig.getGlobalCostMultiplier();
        int cost = (int) Math.round(baseCost * Math.pow(mult, extraSlots) * globalMult);

        boolean isOp = player.hasPermissions(2);
        if (!isOp && player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal("§cYou do not have enough XP levels! (Cost: " + cost + " levels, Current: " + player.experienceLevel + " levels)"));
            return 0;
        }

        if (!isOp) {
            player.giveExperienceLevels(-cost);
        }

        homeData.setExtraHomeSlots(extraSlots + 1);
        HomeManager.saveHomesData(player.server, player.getUUID(), homeData);

        int totalLimit = XptpConfig.getMaxHomes() + homeData.getExtraHomeSlots();
        player.sendSystemMessage(Component.literal("§aSuccessfully purchased 1 extra home slot! Your new limit is " + totalLimit + " homes."));
        return 1;
    }
}
