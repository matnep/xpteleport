package com.example.ftbnep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FtbNepConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/xpteleport.json");

    private static final Map<String, Integer> costs = new HashMap<>();
    private static String insufficientXpMessage = "§cYou do not have enough XP levels to teleport! (Cost: %s levels, Current: %s levels)";
    private static String xpDeductedMessage = "§aTeleportation successful! Deducted %s XP levels.";
    private static String confirmPromptMessage = "§eYou are about to teleport to [%s, %s, %s] for %s XP levels.";
    
    private static boolean redirectTpToXtp = true;
    private static int maxHomes = 5;
    private static int rtpMinRange = 50;
    private static int rtpMaxRange = 500;

    private static boolean distanceBasedXp = true;
    private static boolean useLogarithmicCost = true;
    private static double baseTeleportCost = -19.3;
    private static double logMultiplier = 5.28;
    private static double xaeroMultiplier = 3.0;
    private static int blocksPerLevel = 500;
    private static int crossDimensionCost = 15;
    private static int warmupSeconds = 3;
    private static int cooldownSeconds = 0;
    private static boolean showOpsOnLeaderboard = false;

    static {
        // Set defaults
        costs.put("tpa", 10);
        costs.put("tpahere", 10);
        costs.put("tpaccept", 10);
        costs.put("home", 10);
        costs.put("warp", 10);
        costs.put("spawn", 10);
        costs.put("rtp", 10);
        costs.put("wild", 10);
        costs.put("back", 10);
        costs.put("xaero_tp", 30);
    }

    public static boolean load() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!CONFIG_FILE.exists()) {
                save();
                return true;
            }

            try (FileReader reader = new FileReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("costs")) {
                    JsonObject costsObj = json.getAsJsonObject("costs");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : costsObj.entrySet()) {
                        costs.put(entry.getKey(), entry.getValue().getAsInt());
                    }
                }
                if (json.has("messages")) {
                    JsonObject msgsObj = json.getAsJsonObject("messages");
                    if (msgsObj.has("insufficient_xp")) {
                        insufficientXpMessage = msgsObj.get("insufficient_xp").getAsString();
                    }
                    if (msgsObj.has("xp_deducted")) {
                        xpDeductedMessage = msgsObj.get("xp_deducted").getAsString();
                    }
                    if (msgsObj.has("confirm_prompt")) {
                        confirmPromptMessage = msgsObj.get("confirm_prompt").getAsString();
                    }
                }
                
                // Read from either the new key redirect_tp_to_xtp or fallback to old redirect_tp_to_ftbtp
                if (json.has("redirect_tp_to_xtp")) {
                    redirectTpToXtp = json.get("redirect_tp_to_xtp").getAsBoolean();
                } else if (json.has("redirect_tp_to_ftbtp")) {
                    redirectTpToXtp = json.get("redirect_tp_to_ftbtp").getAsBoolean();
                }

                if (json.has("max_homes")) {
                    maxHomes = json.get("max_homes").getAsInt();
                }
                if (json.has("rtp_min_range")) {
                    rtpMinRange = json.get("rtp_min_range").getAsInt();
                }
                if (json.has("rtp_max_range")) {
                    rtpMaxRange = json.get("rtp_max_range").getAsInt();
                }
                if (json.has("distance_based_xp")) {
                    distanceBasedXp = json.get("distance_based_xp").getAsBoolean();
                }
                if (json.has("use_logarithmic_cost")) {
                    useLogarithmicCost = json.get("use_logarithmic_cost").getAsBoolean();
                }
                if (json.has("base_teleport_cost")) {
                    baseTeleportCost = json.get("base_teleport_cost").getAsDouble();
                }
                if (json.has("log_multiplier")) {
                    logMultiplier = json.get("log_multiplier").getAsDouble();
                }
                if (json.has("xaero_multiplier")) {
                    xaeroMultiplier = json.get("xaero_multiplier").getAsDouble();
                }
                if (json.has("blocks_per_level")) {
                    blocksPerLevel = json.get("blocks_per_level").getAsInt();
                }
                if (json.has("cross_dimension_cost")) {
                    crossDimensionCost = json.get("cross_dimension_cost").getAsInt();
                }
                if (json.has("warmup_seconds")) {
                    warmupSeconds = json.get("warmup_seconds").getAsInt();
                }
                if (json.has("cooldown_seconds")) {
                    cooldownSeconds = json.get("cooldown_seconds").getAsInt();
                }
                if (json.has("show_ops_on_leaderboard")) {
                    showOpsOnLeaderboard = json.get("show_ops_on_leaderboard").getAsBoolean();
                }
            }
            LOGGER.info("XPTeleport config loaded successfully.");
            save(); // Automatically migrate and write help comments / README
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to load XPTeleport config", e);
            return false;
        }
    }

    public static void save() {
        try {
            JsonObject json = new JsonObject();
            
            // Inline helpful header comments
            json.addProperty("_HELP_HEADER", "=== XPTELEPORT MOD CONFIGURATION GUIDE ===");
            json.addProperty("_HELP_INFO_1", "Modify values here to change teleport costs and rules.");
            json.addProperty("_HELP_INFO_2", "Run '/xpteleport reload' in game to apply changes instantly!");

            JsonObject costsObj = new JsonObject();
            for (Map.Entry<String, Integer> entry : costs.entrySet()) {
                costsObj.addProperty(entry.getKey(), entry.getValue());
            }
            json.add("costs", costsObj);

            JsonObject msgsObj = new JsonObject();
            msgsObj.addProperty("insufficient_xp", insufficientXpMessage);
            msgsObj.addProperty("xp_deducted", xpDeductedMessage);
            msgsObj.addProperty("confirm_prompt", confirmPromptMessage);
            json.add("messages", msgsObj);

            json.addProperty("redirect_tp_to_xtp", redirectTpToXtp);
            json.addProperty("_HELP_redirect_tp_to_xtp", "Redirects non-OP /tp coordinate clicks to map confirm dialog.");
            
            json.addProperty("max_homes", maxHomes);
            json.addProperty("_HELP_max_homes", "Maximum homes a player can set (e.g. 5).");
            
            json.addProperty("rtp_min_range", rtpMinRange);
            json.addProperty("rtp_max_range", rtpMaxRange);
            json.addProperty("_HELP_rtp_range", "Minimum and maximum block radius around spawn to search for landing on /rtp.");

            json.addProperty("distance_based_xp", distanceBasedXp);
            json.addProperty("_HELP_distance_based_xp", "Calculate XP cost dynamically based on distance. If false, falls back to flat rates in 'costs'.");
            
            json.addProperty("use_logarithmic_cost", useLogarithmicCost);
            json.addProperty("_HELP_use_logarithmic_cost", "Use logarithmic distance scaling. If true, uses logarithmic formula (100 blocks = 5 levels). If false, uses blocks_per_level.");
            
            json.addProperty("base_teleport_cost", baseTeleportCost);
            json.addProperty("log_multiplier", logMultiplier);
            json.addProperty("_HELP_log_formula", "Formula: cost = base + multiplier * ln(distance). Default (-19.3, 5.28) forces 100 blocks = 5 levels, capping at 30 levels.");
            
            json.addProperty("xaero_multiplier", xaeroMultiplier);
            json.addProperty("_HELP_xaero_multiplier", "Cost multiplier applied to Xaero map clicks and shared chat waypoints (e.g. 3.0 = triple cost).");
            
            json.addProperty("blocks_per_level", blocksPerLevel);
            json.addProperty("_HELP_blocks_per_level", "Linear cost scaling divisor (used if use_logarithmic_cost is false).");
            
            json.addProperty("cross_dimension_cost", crossDimensionCost);
            json.addProperty("_HELP_cross_dimension_cost", "Flat XP level cost charged for teleports across dimensions.");
            
            json.addProperty("warmup_seconds", warmupSeconds);
            json.addProperty("_HELP_warmup_seconds", "Delay in seconds where a player must stand still and take no damage before teleporting.");
            
            json.addProperty("cooldown_seconds", cooldownSeconds);
            json.addProperty("_HELP_cooldown_seconds", "Delay in seconds between successful teleports. Set to 0 to disable cooldowns.");

            json.addProperty("show_ops_on_leaderboard", showOpsOnLeaderboard);
            json.addProperty("_HELP_show_ops_on_leaderboard", "Set to true to show server operators (OPs) on the XP leaderboard.");

            try (FileWriter writer = new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            LOGGER.info("XPTeleport config saved successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to save XPTeleport config", e);
        }
    }

    public static int getCost(String command) {
        return costs.getOrDefault(command.toLowerCase(), 0);
    }

    public static int getXaeroTpCost() {
        return costs.getOrDefault("xaero_tp", 30);
    }

    public static String getInsufficientXpMessage() {
        return insufficientXpMessage;
    }

    public static String getXpDeductedMessage() {
        return xpDeductedMessage;
    }

    public static String getConfirmPromptMessage() {
        return confirmPromptMessage;
    }

    public static boolean isRedirectTpToXtp() {
        return redirectTpToXtp;
    }

    public static void setRedirectTpToXtp(boolean value) {
        redirectTpToXtp = value;
    }

    public static int getMaxHomes() {
        return maxHomes;
    }

    public static int getRtpMinRange() {
        return rtpMinRange;
    }

    public static int getRtpMaxRange() {
        return rtpMaxRange;
    }

    public static boolean isDistanceBasedXp() {
        return distanceBasedXp;
    }

    public static boolean isUseLogarithmicCost() {
        return useLogarithmicCost;
    }

    public static double getBaseTeleportCost() {
        return baseTeleportCost;
    }

    public static double getLogMultiplier() {
        return logMultiplier;
    }

    public static double getXaeroMultiplier() {
        return xaeroMultiplier;
    }

    public static int getBlocksPerLevel() {
        return blocksPerLevel;
    }

    public static int getCrossDimensionCost() {
        return crossDimensionCost;
    }

    public static int getWarmupSeconds() {
        return warmupSeconds;
    }

    public static int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public static boolean isShowOpsOnLeaderboard() {
        return showOpsOnLeaderboard;
    }

    public static void setShowOpsOnLeaderboard(boolean value) {
        showOpsOnLeaderboard = value;
    }
}
