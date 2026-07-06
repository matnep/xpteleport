package com.example.ftbnep;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
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
import net.minecraft.world.phys.Vec2;
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
import java.util.concurrent.ConcurrentHashMap;

@Mod("xpteleport")
public class FtbNep {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();

    // Regex to match Xaero Shared Waypoints in chat
    private static final java.util.regex.Pattern WAYPOINT_PATTERN = java.util.regex.Pattern.compile(
        "xaero-waypoint:([^:]+):([^:]+):([^:]+):(-?[0-9]+):(-?[0-9]+):(-?[0-9]+)"
    );

    // Cache for pending Xaero Map teleports
    private static final Map<UUID, PendingXaeroTeleport> pendingXaeroTeleports = new ConcurrentHashMap<>();

    public FtbNep(IEventBus modEventBus) {
        LOGGER.info("Initializing Standalone FTB Essentials Replacement (ftbnep)...");
        
        // Load config and warps
        FtbNepConfig.load();
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
        if (!FtbNepConfig.isRedirectTpToXtp()) return false;
        if (player.hasPermissions(2)) return false; // OPs bypass redirection

        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1);
        }

        String[] parts = commandLine.split("\\s+");
        if (parts.length < 4) return false;

        String baseCmd = parts[0].toLowerCase();
        if (!baseCmd.equals("tp") && !baseCmd.equals("teleport")) return false;

        int coordStartIndex = 1;
        if (parts[1].equalsIgnoreCase("@s") || parts[1].equalsIgnoreCase(player.getGameProfile().getName())) {
            coordStartIndex = 2;
        }

        if (parts.length < coordStartIndex + 3) return false;

        try {
            double x = parseCoordinate(player.getX(), parts[coordStartIndex]);
            double y = parseCoordinate(player.getY(), parts[coordStartIndex + 1]);
            double z = parseCoordinate(player.getZ(), parts[coordStartIndex + 2]);

            Float yaw = null;
            Float pitch = null;
            if (parts.length > coordStartIndex + 3) {
                yaw = (float) parseCoordinate(player.getYRot(), parts[coordStartIndex + 3]);
            }
            if (parts.length > coordStartIndex + 4) {
                pitch = (float) parseCoordinate(player.getXRot(), parts[coordStartIndex + 4]);
            }

            event.setCanceled(true);
            initXaeroTp(player, new Vec3(x, y, z), yaw, pitch);
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

    public static int calculateXpCost(ServerPlayer player, TeleportLocation destination, boolean isXaero) {
        // OPs bypass XP charging completely
        if (player.hasPermissions(2)) {
            return 0;
        }

        if (!FtbNepConfig.isDistanceBasedXp()) {
            return isXaero ? FtbNepConfig.getXaeroTpCost() : 10;
        }

        // Cross-dimension cost check
        if (!player.level().dimension().location().toString().equals(destination.dimension())) {
            double cost = FtbNepConfig.getCrossDimensionCost();
            if (isXaero) {
                cost *= FtbNepConfig.getXaeroMultiplier();
            }
            return Math.max(0, (int) Math.round(cost));
        }

        // Calculate 3D distance
        double dx = player.getX() - destination.x();
        double dy = player.getY() - destination.y();
        double dz = player.getZ() - destination.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double calculatedCost;
        if (FtbNepConfig.isUseLogarithmicCost()) {
            double base = FtbNepConfig.getBaseTeleportCost();
            double mult = FtbNepConfig.getLogMultiplier();
            calculatedCost = base + mult * Math.log(Math.max(1.0, distance));
        } else {
            double base = FtbNepConfig.getBaseTeleportCost();
            double bpl = FtbNepConfig.getBlocksPerLevel();
            calculatedCost = base + (distance / (bpl <= 0 ? 500.0 : bpl));
        }

        if (isXaero) {
            calculatedCost *= FtbNepConfig.getXaeroMultiplier();
        }

        return Math.max(0, (int) Math.round(calculatedCost));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. Mod Config / Administration Command
        dispatcher.register(Commands.literal("xpteleport")
            .then(Commands.literal("info")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("§aThis mod is created by §bMatnepp §afrom §dBurhan Bistro <3"), false);
                    return 1;
                })
            )
            .then(Commands.literal("help")
                .executes(this::executeHelp)
            )
            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    boolean success = FtbNepConfig.load();
                    WarpManager.load();
                    if (success) {
                        context.getSource().sendSuccess(() -> Component.literal("§aXPTeleport configuration and warps reloaded successfully!"), true);
                    } else {
                        context.getSource().sendFailure(Component.literal("§cFailed to reload XPTeleport configuration. Check console log for details."));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("change")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    boolean nextVal = !FtbNepConfig.isRedirectTpToXtp();
                    FtbNepConfig.setRedirectTpToXtp(nextVal);
                    FtbNepConfig.save();
                    context.getSource().sendSuccess(() -> Component.literal("§aXPTeleport auto-redirection toggled to: " + nextVal), true);
                    return 1;
                })
            )
        );

        // 2. Client Xaero Map custom teleport redirection commands
        dispatcher.register(Commands.literal("xtp")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("confirm")
                .executes(this::executeConfirmTp)
            )
            .then(Commands.argument("pos", Vec3Argument.vec3())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Vec3 pos = Vec3Argument.getVec3(context, "pos");
                    return initXaeroTp(player, pos, null, null);
                })
                .then(Commands.argument("rot", RotationArgument.rotation())
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        Vec3 pos = Vec3Argument.getVec3(context, "pos");
                        Coordinates rotation = RotationArgument.getRotation(context, "rot");
                        Vec2 rot = rotation.getRotation(context.getSource());
                        return initXaeroTp(player, pos, rot.y, rot.x);
                    })
                )
            )
        );

        // 3. TPA Commands
        dispatcher.register(Commands.literal("tpa")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer requester = context.getSource().getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    
                    if (requester == target) {
                        context.getSource().sendFailure(Component.literal("§cYou cannot send a TPA request to yourself."));
                        return 0;
                    }

                    if (!CooldownManager.checkCooldown(requester)) return 0;

                    // Calculate distance-based TPA cost (where requester will teleport to target)
                    TeleportLocation targetLoc = TeleportLocation.of(target);
                    int cost = calculateXpCost(requester, targetLoc, false);

                    if (requester.experienceLevel < cost) {
                        context.getSource().sendFailure(Component.literal(
                            String.format(FtbNepConfig.getInsufficientXpMessage(), cost, requester.experienceLevel)
                        ));
                        return 0;
                    }

                    TpaManager.addRequest(target.getUUID(), requester.getUUID(), false);
                    
                    requester.sendSystemMessage(Component.literal("§aTeleport request sent to " + target.getGameProfile().getName() + ". (Estimated cost: " + cost + " levels)"));
                    
                    MutableComponent prompt = Component.literal("§e" + requester.getGameProfile().getName() + " wants to teleport to you. ")
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
                    ServerPlayer requester = context.getSource().getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");

                    if (requester == target) {
                        context.getSource().sendFailure(Component.literal("§cYou cannot send a TPAHere request to yourself."));
                        return 0;
                    }

                    if (!CooldownManager.checkCooldown(target)) {
                        context.getSource().sendFailure(Component.literal("§cTarget player is on a teleport cooldown."));
                        return 0;
                    }

                    // In TPAHere, the target teleports to the requester. Target pays the cost.
                    TeleportLocation reqLoc = TeleportLocation.of(requester);
                    int cost = calculateXpCost(target, reqLoc, false);
                    
                    TpaManager.addRequest(target.getUUID(), requester.getUUID(), true);

                    requester.sendSystemMessage(Component.literal("§aTeleport-here request sent to " + target.getGameProfile().getName() + "."));

                    MutableComponent prompt = Component.literal("§e" + requester.getGameProfile().getName() + " wants you to teleport to them. ")
                        .append(Component.literal("[ACCEPT]")
                            .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept (Cost: " + cost + " levels)")))))
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
                    // target teleports to requester. Target pays cost.
                    if (!CooldownManager.checkCooldown(target)) return 0;
                    TeleportLocation reqLoc = TeleportLocation.of(requester);
                    int cost = calculateXpCost(target, reqLoc, false);

                    if (target.experienceLevel < cost) {
                        context.getSource().sendFailure(Component.literal(
                            String.format(FtbNepConfig.getInsufficientXpMessage(), cost, target.experienceLevel)
                        ));
                        return 0;
                    }
                    
                    TpaManager.removeRequest(target.getUUID());
                    if (target.hasPermissions(2)) {
                        BackManager.record(target);
                        reqLoc.teleport(target);
                        target.sendSystemMessage(Component.literal("§aTeleported!"), true);
                    } else {
                        WarmupManager.startWarmup(target, reqLoc, cost);
                    }
                    requester.sendSystemMessage(Component.literal("§a" + target.getGameProfile().getName() + " accepted your request. Teleporting..."));
                } else {
                    // requester teleports to target. Requester pays cost.
                    if (!CooldownManager.checkCooldown(requester)) {
                        context.getSource().sendFailure(Component.literal("§cRequester is on a teleport cooldown."));
                        return 0;
                    }
                    TeleportLocation targetLoc = TeleportLocation.of(target);
                    int cost = calculateXpCost(requester, targetLoc, false);

                    if (requester.experienceLevel < cost) {
                        context.getSource().sendFailure(Component.literal(
                            "§cRequester " + requester.getGameProfile().getName() + " no longer has enough XP levels to teleport."
                        ));
                        return 0;
                    }

                    TpaManager.removeRequest(target.getUUID());
                    if (requester.hasPermissions(2)) {
                        BackManager.record(requester);
                        targetLoc.teleport(requester);
                        requester.sendSystemMessage(Component.literal("§aTeleported!"), true);
                    } else {
                        WarmupManager.startWarmup(requester, targetLoc, cost);
                    }
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
                target.sendSystemMessage(Component.literal("§aTeleport request denied."));
                
                ServerPlayer requester = target.server.getPlayerList().getPlayer(req.requester());
                if (requester != null) {
                    requester.sendSystemMessage(Component.literal("§c" + target.getGameProfile().getName() + " denied your teleport request."));
                }
                return 1;
            })
        );

        dispatcher.register(Commands.literal("tpcancel")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                TpaManager.removeRequest(player.getUUID());
                player.sendSystemMessage(Component.literal("§aCleared your teleport request queues."));
                return 1;
            })
        );

        // 4. Homes Commands
        dispatcher.register(Commands.literal("sethome")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                return executeSetHome(context, "home");
            })
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    return executeSetHome(context, StringArgumentType.getString(context, "name"));
                })
            )
        );

        dispatcher.register(Commands.literal("home")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                return executeHome(context, "home");
            })
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    return executeHome(context, StringArgumentType.getString(context, "name"));
                })
            )
        );

        dispatcher.register(Commands.literal("delhome")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                return executeDelHome(context, "home");
            })
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    return executeDelHome(context, StringArgumentType.getString(context, "name"));
                })
            )
        );

        dispatcher.register(Commands.literal("listhomes")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Map<String, TeleportLocation> homes = HomeManager.getHomes(player.server, player.getUUID());
                if (homes.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§cYou have no homes set."));
                } else {
                    String names = String.join(", ", homes.keySet());
                    player.sendSystemMessage(Component.literal("§aYour homes (" + homes.size() + "/" + FtbNepConfig.getMaxHomes() + "): " + names));
                }
                return 1;
            })
        );

        // 5. Warps Commands
        dispatcher.register(Commands.literal("setwarp")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(context, "name");
                    WarpManager.addWarp(name, TeleportLocation.of(player));
                    player.sendSystemMessage(Component.literal("§aWarp '" + name + "' set successfully!"));
                    return 1;
                })
            )
        );

        dispatcher.register(Commands.literal("warp")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(context, "name");
                    TeleportLocation loc = WarpManager.getWarp(name);
                    if (loc == null) {
                        context.getSource().sendFailure(Component.literal("§cWarp '" + name + "' not found."));
                        return 0;
                    }

                    if (!CooldownManager.checkCooldown(player)) return 0;

                    int cost = calculateXpCost(player, loc, false);
                    if (player.experienceLevel < cost) {
                        context.getSource().sendFailure(Component.literal(
                            String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
                        ));
                        return 0;
                    }

                    if (player.hasPermissions(2)) {
                        BackManager.record(player);
                        loc.teleport(player);
                        player.sendSystemMessage(Component.literal("§aTeleported!"), true);
                    } else {
                        WarmupManager.startWarmup(player, loc, cost);
                    }
                    return 1;
                })
            )
        );

        dispatcher.register(Commands.literal("delwarp")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
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
                Map<String, TeleportLocation> warps = WarpManager.getWarps();
                if (warps.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§cNo warps set on this server."));
                } else {
                    String names = String.join(", ", warps.keySet());
                    player.sendSystemMessage(Component.literal("§aAvailable warps: " + names));
                }
                return 1;
            })
        );

        // 6. Spawn Command
        dispatcher.register(Commands.literal("spawn")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (!CooldownManager.checkCooldown(player)) return 0;

                ServerLevel level = player.server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                TeleportLocation spawnLoc = new TeleportLocation(spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5, player.getYRot(), player.getXRot(), level.dimension().location().toString());

                int cost = calculateXpCost(player, spawnLoc, false);
                if (player.experienceLevel < cost) {
                    context.getSource().sendFailure(Component.literal(
                        String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
                    ));
                    return 0;
                }

                if (player.hasPermissions(2)) {
                    BackManager.record(player);
                    spawnLoc.teleport(player);
                    player.sendSystemMessage(Component.literal("§aTeleported!"), true);
                } else {
                    WarmupManager.startWarmup(player, spawnLoc, cost);
                }
                return 1;
            })
        );

        // 7. Back Command
        dispatcher.register(Commands.literal("back")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                TeleportLocation loc = BackManager.getLastLocation(player);
                if (loc == null) {
                    context.getSource().sendFailure(Component.literal("§cNo teleport/death history found."));
                    return 0;
                }

                if (!CooldownManager.checkCooldown(player)) return 0;

                int cost = calculateXpCost(player, loc, false);
                if (player.experienceLevel < cost) {
                    context.getSource().sendFailure(Component.literal(
                        String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
                    ));
                    return 0;
                }

                if (player.hasPermissions(2)) {
                    BackManager.record(player);
                    loc.teleport(player);
                    player.sendSystemMessage(Component.literal("§aTeleported!"), true);
                } else {
                    WarmupManager.startWarmup(player, loc, cost);
                }
                return 1;
            })
        );

        // 8. RTP Command
        dispatcher.register(Commands.literal("rtp")
            .requires(source -> source.isPlayer())
            .executes(this::executeRtp)
        );

        dispatcher.register(Commands.literal("wild")
            .requires(source -> source.isPlayer())
            .executes(this::executeRtp)
        );

        // 9. XP Give and XP Request Commands (and shortcuts)
        dispatcher.register(Commands.literal("xpgive")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                    .executes(this::executeXpGive)
                )
            )
        );
        dispatcher.register(Commands.literal("xpg")
            .requires(source -> source.isPlayer())
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                    .executes(this::executeXpGive)
                )
            )
        );

        dispatcher.register(Commands.literal("xprequest")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("accept")
                .executes(this::executeXpRequestAccept)
            )
            .then(Commands.literal("deny")
                .executes(this::executeXpRequestDeny)
            )
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                    .executes(this::executeXpRequest)
                )
            )
        );
        dispatcher.register(Commands.literal("xpr")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("accept")
                .executes(this::executeXpRequestAccept)
            )
            .then(Commands.literal("deny")
                .executes(this::executeXpRequestDeny)
            )
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                    .executes(this::executeXpRequest)
                )
            )
        );
    }

    private int executeSetHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, TeleportLocation> homes = HomeManager.getHomes(player.server, player.getUUID());

        if (homes.size() >= FtbNepConfig.getMaxHomes() && !homes.containsKey(name.toLowerCase())) {
            context.getSource().sendFailure(Component.literal(
                "§cYou cannot set any more homes! (Limit: " + FtbNepConfig.getMaxHomes() + ")"
            ));
            return 0;
        }

        homes.put(name.toLowerCase(), TeleportLocation.of(player));
        HomeManager.saveHomes(player.server, player.getUUID(), homes);
        player.sendSystemMessage(Component.literal("§aHome '" + name + "' set successfully."));
        return 1;
    }

    private int executeHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, TeleportLocation> homes = HomeManager.getHomes(player.server, player.getUUID());
        TeleportLocation loc = homes.get(name.toLowerCase());

        if (loc == null) {
            context.getSource().sendFailure(Component.literal("§cHome '" + name + "' not found."));
            return 0;
        }

        if (!CooldownManager.checkCooldown(player)) return 0;

        int cost = calculateXpCost(player, loc, false);
        if (player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal(
                String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        if (player.hasPermissions(2)) {
            BackManager.record(player);
            loc.teleport(player);
            player.sendSystemMessage(Component.literal("§aTeleported!"), true);
        } else {
            WarmupManager.startWarmup(player, loc, cost);
        }
        return 1;
    }

    private int executeDelHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, TeleportLocation> homes = HomeManager.getHomes(player.server, player.getUUID());

        if (homes.containsKey(name.toLowerCase())) {
            homes.remove(name.toLowerCase());
            HomeManager.saveHomes(player.server, player.getUUID(), homes);
            player.sendSystemMessage(Component.literal("§aHome '" + name + "' deleted."));
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cHome '" + name + "' not found."));
            return 0;
        }
    }

    private int executeRtp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!CooldownManager.checkCooldown(player)) return 0;

        int cost = FtbNepConfig.getCost("rtp"); // RTP uses flat configured cost
        if (player.experienceLevel < cost) {
            context.getSource().sendFailure(Component.literal(
                String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        int rtpMin = FtbNepConfig.getRtpMinRange();
        int rtpMax = FtbNepConfig.getRtpMaxRange();
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();

        player.sendSystemMessage(Component.literal("§dLooking for safe location..."));

        int attempts = 0;
        BlockPos targetPos = null;
        while (attempts < 3) {
            double distance = rtpMin + RANDOM.nextDouble() * (rtpMax - rtpMin);
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double rx = spawn.getX() + Math.cos(angle) * distance;
            double rz = spawn.getZ() + Math.sin(angle) * distance;

            // Clamp coordinates inside the active World Border
            rx = Math.max(border.getMinX() + 16, Math.min(border.getMaxX() - 16, rx));
            rz = Math.max(border.getMinZ() + 16, Math.min(border.getMaxZ() - 16, rz));

            int ry = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) rx, (int) rz);
            BlockPos pos = new BlockPos((int) rx, ry, (int) rz);
            
            BlockState below = level.getBlockState(pos.below());
            BlockState inside = level.getBlockState(pos);
            BlockState above = level.getBlockState(pos.above());

            // Check for safe blocks (not lava, not source fluid, has air space)
            if (!below.isAir() && below.getFluidState().isEmpty() && inside.isAir() && above.isAir()) {
                targetPos = pos;
                break;
            }
            attempts++;
        }

        if (targetPos == null) {
            context.getSource().sendFailure(Component.literal("§cCould not find a safe location to teleport to! Try again."));
            return 0;
        }

        TeleportLocation dest = new TeleportLocation(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, player.getYRot(), player.getXRot(), level.dimension().location().toString());
        if (player.hasPermissions(2)) {
            BackManager.record(player);
            dest.teleport(player);
            player.sendSystemMessage(Component.literal("§aTeleported!"), true);
        } else {
            WarmupManager.startWarmup(player, dest, cost);
        }
        return 1;
    }

    private int initXaeroTp(ServerPlayer player, Vec3 pos, Float yaw, Float pitch) {
        if (!CooldownManager.checkCooldown(player)) return 0;

        float y = yaw != null ? yaw : player.getYRot();
        float p = pitch != null ? pitch : player.getXRot();
        TeleportLocation dest = new TeleportLocation(pos.x, pos.y, pos.z, y, p, player.level().dimension().location().toString());

        // Calculate cost using isXaero = true
        int cost = calculateXpCost(player, dest, true);

        if (player.experienceLevel < cost) {
            player.sendSystemMessage(Component.literal(
                String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            return 0;
        }

        // Store pending teleport context
        pendingXaeroTeleports.put(player.getUUID(), new PendingXaeroTeleport(dest, cost, System.currentTimeMillis()));

        // Format prompt message
        String prompt = String.format(FtbNepConfig.getConfirmPromptMessage(), 
            String.format("%.1f", pos.x), 
            String.format("%.1f", pos.y), 
            String.format("%.1f", pos.z), 
            cost
        );

        // Clickable [CONFIRM] component
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

    private int executeConfirmTp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID uuid = player.getUUID();
        
        PendingXaeroTeleport pending = pendingXaeroTeleports.get(uuid);
        if (pending == null || (System.currentTimeMillis() - pending.timestamp) > 30000) {
            player.sendSystemMessage(Component.literal("§cYou do not have a pending map teleport request, or it has expired."));
            pendingXaeroTeleports.remove(uuid);
            return 0;
        }

        if (!CooldownManager.checkCooldown(player)) {
            pendingXaeroTeleports.remove(uuid);
            return 0;
        }

        int cost = pending.cost;
        if (player.experienceLevel < cost) {
            player.sendSystemMessage(Component.literal(
                String.format(FtbNepConfig.getInsufficientXpMessage(), cost, player.experienceLevel)
            ));
            pendingXaeroTeleports.remove(uuid);
            return 0;
        }

        pendingXaeroTeleports.remove(uuid);
        if (player.hasPermissions(2)) {
            BackManager.record(player);
            pending.destination.teleport(player);
            player.sendSystemMessage(Component.literal("§aTeleported!"), true);
        } else {
            WarmupManager.startWarmup(player, pending.destination, cost);
        }
        return 1;
    }

    private int executeXpGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int levels = IntegerArgumentType.getInteger(context, "levels");

        if (sender == target) {
            context.getSource().sendFailure(Component.literal("§cYou cannot give XP to yourself."));
            return 0;
        }

        // Admins (OP) can give infinite XP without deducting from themselves
        if (!sender.hasPermissions(2) && sender.experienceLevel < levels) {
            context.getSource().sendFailure(Component.literal(
                String.format("§cYou do not have enough XP levels! (Required: %s, Current: %s)", levels, sender.experienceLevel)
            ));
            return 0;
        }

        if (!sender.hasPermissions(2)) {
            sender.giveExperienceLevels(-levels);
        }
        target.giveExperienceLevels(levels);

        sender.sendSystemMessage(Component.literal("§aYou gave §6" + levels + "§a XP levels to " + target.getGameProfile().getName() + "."));
        target.sendSystemMessage(Component.literal("§aYou received §6" + levels + "§a XP levels from " + sender.getGameProfile().getName() + "."));
        return 1;
    }

    private int executeXpRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer requester = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int levels = IntegerArgumentType.getInteger(context, "levels");

        if (requester == target) {
            context.getSource().sendFailure(Component.literal("§cYou cannot request XP from yourself."));
            return 0;
        }

        XpRequestManager.addRequest(target.getUUID(), requester.getUUID(), levels);

        requester.sendSystemMessage(Component.literal("§aXP request sent to " + target.getGameProfile().getName() + " for §6" + levels + "§a levels."));

        MutableComponent prompt = Component.literal("§e" + requester.getGameProfile().getName() + " is requesting §6" + levels + "§e levels of XP from you. ")
            .append(Component.literal("[ACCEPT]")
                .withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xprequest accept"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to give " + levels + " levels to " + requester.getGameProfile().getName())))))
            .append(" | ")
            .append(Component.literal("[DENY]")
                .withStyle(style -> style
                    .withColor(ChatFormatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xprequest deny"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to deny this request")))));
        
        target.sendSystemMessage(prompt);
        return 1;
    }

    private int executeXpRequestAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
            context.getSource().sendFailure(Component.literal(
                String.format("§cYou do not have enough XP levels to accept this! (Required: %s, Current: %s)", levels, target.experienceLevel)
            ));
            return 0;
        }

        XpRequestManager.removeRequest(target.getUUID());

        target.giveExperienceLevels(-levels);
        requester.giveExperienceLevels(levels);

        target.sendSystemMessage(Component.literal("§aYou gave §6" + levels + "§a XP levels to " + requester.getGameProfile().getName() + "."));
        requester.sendSystemMessage(Component.literal("§aYou received §6" + levels + "§a XP levels from " + target.getGameProfile().getName() + "."));
        return 1;
    }

    private int executeXpRequestDeny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    private int executeHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("§6§l=== XPTeleport Command Reference ==="));
        source.sendSystemMessage(Component.literal("§dXP Trading:"));
        source.sendSystemMessage(Component.literal("§7- §b/xpgive <player> <levels> §7(alias: §b/xpg§7) - Give XP levels"));
        source.sendSystemMessage(Component.literal("§7- §b/xprequest <player> <levels> §7(alias: §b/xpr§7) - Request XP levels"));
        source.sendSystemMessage(Component.literal("§dTeleportation:"));
        source.sendSystemMessage(Component.literal("§7- §b/tpa <player> §7/ §b/tpahere <player> §7- Teleport requests"));
        source.sendSystemMessage(Component.literal("§7- §b/tpaccept §7/ §b/tpdeny §7/ §b/tpcancel §7- Manage TPA"));
        source.sendSystemMessage(Component.literal("§7- §b/home [name] §7/ §b/sethome [name] §7/ §b/delhome [name] §7- Personal homes"));
        source.sendSystemMessage(Component.literal("§7- §b/warp <name> §7/ §b/listwarps §7- Global warps"));
        source.sendSystemMessage(Component.literal("§7- §b/spawn §7- Teleport to spawn"));
        source.sendSystemMessage(Component.literal("§7- §b/back §7- Return to last location"));
        source.sendSystemMessage(Component.literal("§7- §b/rtp §7(alias: §b/wild§7) - Random teleport"));
        source.sendSystemMessage(Component.literal("§dAdministration:"));
        source.sendSystemMessage(Component.literal("§7- §b/xpteleport info §7- Credits"));
        source.sendSystemMessage(Component.literal("§7- §b/xpteleport reload §7(OP) - Reload configs"));
        source.sendSystemMessage(Component.literal("§7- §b/xpteleport change §7(OP) - Toggle auto-redirection"));
        source.sendSystemMessage(Component.literal("§6§l================================="));
        return 1;
    }

    private record PendingXaeroTeleport(TeleportLocation destination, int cost, long timestamp) {}
}
