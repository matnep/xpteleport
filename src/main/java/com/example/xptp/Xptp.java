package com.example.xptp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@Mod("xpteleport")
public class Xptp {
    private static final Logger LOGGER = LogManager.getLogger();

    // Regex to match Xaero Shared Waypoints in chat
    private static final java.util.regex.Pattern WAYPOINT_PATTERN = java.util.regex.Pattern.compile(
        "xaero-waypoint:([^:]+):([^:]+):([^:]+):(-?[0-9]+):(-?[0-9]+):(-?[0-9]+)"
    );

    public Xptp(IEventBus modEventBus) {
        LOGGER.info("Initializing XPTeleport (xptp)...");
        
        // Load config and warps
        XptpConfig.load();
        WarpManager.load();

        // Register event handlers on NeoForge Game event bus
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String raw = event.getRawText();
        java.util.regex.Matcher matcher = WAYPOINT_PATTERN.matcher(raw);
        if (matcher.find()) {
            try {
                int x = Integer.parseInt(matcher.group(4));
                int y = Integer.parseInt(matcher.group(5));
                int z = Integer.parseInt(matcher.group(6));

                // Append the clickable golden [Teleport] button
                MutableComponent original = event.getMessage().copy();
                
                MutableComponent teleportBtn = Component.literal(" ")
                    .append(Component.literal("[Teleport]")
                        .withStyle(style -> style
                            .withColor(ChatFormatting.GOLD)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xtp " + x + " " + y + " " + z))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view XP cost and teleport!")))
                        )
                    );
                
                event.setMessage(original.append(teleportBtn));
            } catch (Exception e) {
                // Ignore parsing exceptions
            }
        }
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (source.isPlayer()) {
            try {
                ServerPlayer player = source.getPlayerOrException();
                String commandLine = event.getParseResults().getReader().getString();
                if (tryRedirectTp(event, player, commandLine)) {
                    return;
                }
            } catch (CommandSyntaxException e) {
                // Ignore non-player executions
            }
        }
    }

    private boolean tryRedirectTp(CommandEvent event, ServerPlayer player, String commandLine) {
        if (!XptpConfig.isRedirectTpToXtp()) return false;
        if (player.hasPermissions(2)) return false; // OPs bypass redirection

        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1);
        }

        List<String> parts = tokenize(commandLine);
        if (parts.size() < 4) return false;

        String baseCmd = parts.get(0).toLowerCase();
        if (!baseCmd.equals("tp") && !baseCmd.equals("teleport")) return false;

        int coordStartIndex = 1;
        if (parts.get(1).equalsIgnoreCase("@s") || parts.get(1).equalsIgnoreCase(player.getGameProfile().getName())) {
            coordStartIndex = 2;
        }

        if (parts.size() < coordStartIndex + 3) return false;

        try {
            double x = parseCoordinate(player.getX(), parts.get(coordStartIndex));
            double y = parseCoordinate(player.getY(), parts.get(coordStartIndex + 1));
            double z = parseCoordinate(player.getZ(), parts.get(coordStartIndex + 2));

            Float yaw = null;
            Float pitch = null;
            if (parts.size() > coordStartIndex + 3) {
                yaw = (float) parseCoordinate(player.getYRot(), parts.get(coordStartIndex + 3));
            }
            if (parts.size() > coordStartIndex + 4) {
                pitch = (float) parseCoordinate(player.getXRot(), parts.get(coordStartIndex + 4));
            }

            event.setCanceled(true);
            TeleportCommands.initXaeroTp(player, new Vec3(x, y, z), yaw, pitch);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double parseCoordinate(double current, String input) {
        if (input.startsWith("~")) {
            if (input.length() == 1) {
                return current;
            }
            return current + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }

    public static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        int bracketDepth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (c == '[' && !inQuotes) {
                bracketDepth++;
                sb.append(c);
            } else if (c == ']' && !inQuotes) {
                bracketDepth--;
                sb.append(c);
            } else if (Character.isWhitespace(c) && !inQuotes && bracketDepth == 0) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    public static boolean isValidName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 32 && name.matches("^[a-zA-Z0-9_]+$");
    }

    public static void performTeleport(ServerPlayer player, TeleportLocation destination, int cost) {
        if (player.hasPermissions(2)) {
            BackManager.record(player);
            destination.teleport(player);
            player.sendSystemMessage(Component.literal("§aTeleported!"), true);
        } else {
            WarmupManager.startWarmup(player, destination, cost);
        }
    }

    public static int calculateXpCost(ServerPlayer player, TeleportLocation destination, boolean isXaero) {
        // OPs bypass XP charging completely
        if (player.hasPermissions(2)) {
            return 0;
        }

        if (!XptpConfig.isDistanceBasedXp()) {
            return isXaero ? XptpConfig.getXaeroTpCost() : 10;
        }

        // Cross-dimension cost check
        if (!player.level().dimension().location().toString().equals(destination.dimension())) {
            double cost = XptpConfig.getCrossDimensionCost();
            if (isXaero) {
                cost *= XptpConfig.getXaeroMultiplier();
            }
            return Math.max(0, (int) Math.round(cost));
        }

        // Calculate 3D distance
        double dx = player.getX() - destination.x();
        double dy = player.getY() - destination.y();
        double dz = player.getZ() - destination.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double calculatedCost;
        if (XptpConfig.isUseLogarithmicCost()) {
            double base = XptpConfig.getBaseTeleportCost();
            double mult = XptpConfig.getLogMultiplier();
            calculatedCost = base + mult * Math.log(Math.max(1.0, distance));
        } else {
            double base = XptpConfig.getBaseTeleportCost();
            double bpl = XptpConfig.getBlocksPerLevel();
            calculatedCost = base + (distance / (bpl <= 0 ? 500.0 : bpl));
        }

        if (isXaero) {
            calculatedCost *= XptpConfig.getXaeroMultiplier();
        }

        return Math.max(0, (int) Math.round(calculatedCost));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        HomeCommands.register(dispatcher);
        WarpCommands.register(dispatcher);
        TpaCommands.register(dispatcher);
        XpCommands.register(dispatcher);
        TeleportCommands.register(dispatcher);
        AdminCommands.register(dispatcher);
    }
}
