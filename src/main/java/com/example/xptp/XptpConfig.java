package com.example.xptp;

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

public class XptpConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/xpteleport.json");

    private static final Map<String, Integer> costs = new HashMap<>();
    private static String insufficientXpMessage = "§cYou do not have enough XP levels to teleport! (Cost: %s levels, Current: %s levels)";
    private static String xpDeductedMessage = "§aTeleportation successful! Deducted %s XP levels.";
    private static String confirmPromptMessage = "§eYou are about to teleport to [%s, %s, %s] for %s XP levels.";
    
    private static double globalCostMultiplier = 1.0;
    private static boolean redirectTpToXtp = true;
    private static int maxHomes = 5;
    private static int buyHomeSlotCost = 15;
    private static double buyHomeSlotMultiplier = 1.0;
    private static int maxExtraHomeSlots = 5;
    private static int buyWarpCost = 50;
    private static double creatorWarpCostMultiplier = 0.0;
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

    // Timeout settings (in seconds)
    private static int tpaRequestTimeoutSeconds = 60;
    private static int xaeroConfirmTimeoutSeconds = 30;
    private static int leaderboardRefreshSeconds = 300;

    // Flat cost and cooldown options
    private static boolean homeDistanceBased = false;
    private static boolean backDistanceBased = true;
    private static boolean deathBackDistanceBased = false;
    private static final Map<String, Integer> cooldowns = new HashMap<>();

    static {
        // Set defaults
        costs.put("tpa", 10);
        costs.put("tpahere", 10);
        costs.put("tpaccept", 10);
        costs.put("home", 2);
        costs.put("warp", 10);
        costs.put("spawn", 10);
        costs.put("rtp", 10);
        costs.put("wild", 10);
        costs.put("back", 10);
        costs.put("death_back", 10);
        costs.put("xaero_tp", 30);

        cooldowns.put("tpa", 0);
        cooldowns.put("tpahere", 0);
        cooldowns.put("tpaccept", 0);
        cooldowns.put("home", 60);
        cooldowns.put("warp", 0);
        cooldowns.put("spawn", 0);
        cooldowns.put("rtp", 120);
        cooldowns.put("wild", 120);
        cooldowns.put("back", 0);
        cooldowns.put("death_back", 0);
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
                
                if (json.has("redirect_tp_to_xtp")) {
                    redirectTpToXtp = json.get("redirect_tp_to_xtp").getAsBoolean();
                }

                if (json.has("global_cost_multiplier")) {
                    globalCostMultiplier = json.get("global_cost_multiplier").getAsDouble();
                }
                if (json.has("max_homes")) {
                    maxHomes = json.get("max_homes").getAsInt();
                }
                if (json.has("buy_home_slot_cost")) {
                    buyHomeSlotCost = json.get("buy_home_slot_cost").getAsInt();
                }
                if (json.has("buy_home_slot_multiplier")) {
                    buyHomeSlotMultiplier = json.get("buy_home_slot_multiplier").getAsDouble();
                }
                if (json.has("max_extra_home_slots")) {
                    maxExtraHomeSlots = json.get("max_extra_home_slots").getAsInt();
                }
                if (json.has("buy_warp_cost")) {
                    buyWarpCost = json.get("buy_warp_cost").getAsInt();
                }
                if (json.has("creator_warp_cost_multiplier")) {
                    creatorWarpCostMultiplier = json.get("creator_warp_cost_multiplier").getAsDouble();
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
                if (json.has("tpa_request_timeout_seconds")) {
                    tpaRequestTimeoutSeconds = json.get("tpa_request_timeout_seconds").getAsInt();
                }
                if (json.has("xaero_confirm_timeout_seconds")) {
                    xaeroConfirmTimeoutSeconds = json.get("xaero_confirm_timeout_seconds").getAsInt();
                }
                if (json.has("leaderboard_refresh_seconds")) {
                    leaderboardRefreshSeconds = json.get("leaderboard_refresh_seconds").getAsInt();
                }
                if (json.has("home_distance_based")) {
                    homeDistanceBased = json.get("home_distance_based").getAsBoolean();
                }
                if (json.has("back_distance_based")) {
                    backDistanceBased = json.get("back_distance_based").getAsBoolean();
                }
                if (json.has("death_back_distance_based")) {
                    deathBackDistanceBased = json.get("death_back_distance_based").getAsBoolean();
                }
                if (json.has("cooldowns")) {
                    JsonObject cdObj = json.getAsJsonObject("cooldowns");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : cdObj.entrySet()) {
                        cooldowns.put(entry.getKey().toLowerCase(), entry.getValue().getAsInt());
                    }
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
            
            json.addProperty("//_global_cost_multiplier", "Master scale factor for all XP costs (teleports and buys). 2.0 = double, 0.5 = half.");
            json.addProperty("global_cost_multiplier", globalCostMultiplier);

            JsonObject costsObj = new JsonObject();
            for (Map.Entry<String, Integer> entry : costs.entrySet()) {
                costsObj.addProperty(entry.getKey(), entry.getValue());
            }
            json.addProperty("//_costs", "Flat rates (in XP levels) charged for commands when distance_based_xp is set to false.");
            json.add("costs", costsObj);

            JsonObject msgsObj = new JsonObject();
            msgsObj.addProperty("insufficient_xp", insufficientXpMessage);
            msgsObj.addProperty("xp_deducted", xpDeductedMessage);
            msgsObj.addProperty("confirm_prompt", confirmPromptMessage);
            json.addProperty("//_messages", "Customizable chat translation strings.");
            json.add("messages", msgsObj);

            json.addProperty("//_redirect_tp_to_xtp", "Redirects non-OP /tp coordinate clicks to map confirm dialog.");
            json.addProperty("redirect_tp_to_xtp", redirectTpToXtp);
            
            json.addProperty("//_max_homes", "Default home slots limit a player starts with before upgrades.");
            json.addProperty("max_homes", maxHomes);
            
            json.addProperty("//_buy_home_slot_cost", "Base XP cost to purchase 1 extra home slot above starting limit.");
            json.addProperty("buy_home_slot_cost", buyHomeSlotCost);
            
            json.addProperty("//_buy_home_slot_multiplier", "Multiplier for subsequent home slot purchases (e.g. 1.5). Set to 1.0 for flat price.");
            json.addProperty("buy_home_slot_multiplier", buyHomeSlotMultiplier);
            
            json.addProperty("//_max_extra_home_slots", "Max extra home slots a player can buy.");
            json.addProperty("max_extra_home_slots", maxExtraHomeSlots);
            
            json.addProperty("//_buy_warp_cost", "XP cost for players to buy a global warp. Only the creator or OPs can delete it.");
            json.addProperty("buy_warp_cost", buyWarpCost);
            
            json.addProperty("//_creator_warp_cost_multiplier", "Cost multiplier applied when teleporting to a warp you created. Set to 0.0 for free of charge, or 0.5 for half price.");
            json.addProperty("creator_warp_cost_multiplier", creatorWarpCostMultiplier);
            
            json.addProperty("//_rtp_range", "Minimum and maximum block radius search range around spawn on /rtp.");
            json.addProperty("rtp_min_range", rtpMinRange);
            json.addProperty("rtp_max_range", rtpMaxRange);

            json.addProperty("//_distance_based_xp", "Calculate XP cost dynamically based on distance. If false, falls back to flat rates in 'costs'.");
            json.addProperty("distance_based_xp", distanceBasedXp);
            
            json.addProperty("//_use_logarithmic_cost", "Use logarithmic distance scaling. If true, uses logarithmic formula (100 blocks = 5 levels). If false, uses blocks_per_level.");
            json.addProperty("use_logarithmic_cost", useLogarithmicCost);
            
            json.addProperty("//_log_formula", "Formula parameters: cost = base + multiplier * ln(distance). Calibrated to 100 blocks = 5 levels.");
            json.addProperty("base_teleport_cost", baseTeleportCost);
            json.addProperty("log_multiplier", logMultiplier);
            
            json.addProperty("//_xaero_multiplier", "Cost multiplier applied to Xaero map clicks and shared chat waypoints (e.g. 3.0 = triple cost).");
            json.addProperty("xaero_multiplier", xaeroMultiplier);
            
            json.addProperty("//_blocks_per_level", "Linear cost scaling divisor (used if use_logarithmic_cost is false).");
            json.addProperty("blocks_per_level", blocksPerLevel);
            
            json.addProperty("//_cross_dimension_cost", "Flat XP level cost charged for teleports across dimensions.");
            json.addProperty("cross_dimension_cost", crossDimensionCost);
            
            json.addProperty("//_warmup_seconds", "Delay in seconds where a player must stand still and take no damage before teleporting.");
            json.addProperty("warmup_seconds", warmupSeconds);
            
            json.addProperty("//_cooldown_seconds", "Delay in seconds between successful teleports. Set to 0 to disable cooldowns.");
            json.addProperty("cooldown_seconds", cooldownSeconds);

            json.addProperty("//_show_ops_on_leaderboard", "Set to true to show server operators (OPs) on the XP leaderboard.");
            json.addProperty("show_ops_on_leaderboard", showOpsOnLeaderboard);

            json.addProperty("//_tpa_request_timeout_seconds", "Time in seconds before a pending TPA request expires.");
            json.addProperty("tpa_request_timeout_seconds", tpaRequestTimeoutSeconds);

            json.addProperty("//_xaero_confirm_timeout_seconds", "Time in seconds before a pending Xaero coordinate teleport confirmation expires.");
            json.addProperty("xaero_confirm_timeout_seconds", xaeroConfirmTimeoutSeconds);

            json.addProperty("//_leaderboard_refresh_seconds", "Time in seconds between automatic background refreshes of the XP leaderboard.");
            json.addProperty("leaderboard_refresh_seconds", leaderboardRefreshSeconds);

            json.addProperty("//_home_distance_based", "Set to true to calculate /home cost based on distance. If false, uses flat cost in 'costs'.");
            json.addProperty("home_distance_based", homeDistanceBased);

            json.addProperty("//_back_distance_based", "Set to true to calculate /back cost based on distance. If false, uses flat cost in 'costs'.");
            json.addProperty("back_distance_based", backDistanceBased);

            json.addProperty("//_death_back_distance_based", "Set to true to calculate /back after death cost based on distance. If false, uses flat cost in 'costs'.");
            json.addProperty("death_back_distance_based", deathBackDistanceBased);

            JsonObject cdObj = new JsonObject();
            for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
                cdObj.addProperty(entry.getKey(), entry.getValue());
            }
            json.addProperty("//_cooldowns", "Cooldown in seconds for specific commands. Set to 0 to disable cooldown.");
            json.add("cooldowns", cdObj);

            java.nio.file.Path targetPath = CONFIG_FILE.toPath();
            java.nio.file.Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
            java.io.File tempFile = tempPath.toFile();
            
            java.io.File parent = tempFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (java.io.FileWriter writer = new java.io.FileWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            
            try {
                java.nio.file.Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            LOGGER.info("XPTeleport config saved successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to save XPTeleport config", e);
        }
    }

    public static int getCost(String command) {
        return (int) Math.round(costs.getOrDefault(command.toLowerCase(), 0) * globalCostMultiplier);
    }

    public static int getXaeroTpCost() {
        return (int) Math.round(costs.getOrDefault("xaero_tp", 30) * globalCostMultiplier);
    }

    public static double getGlobalCostMultiplier() {
        return globalCostMultiplier;
    }

    public static int getBuyHomeSlotCost() {
        return buyHomeSlotCost;
    }

    public static double getBuyHomeSlotMultiplier() {
        return buyHomeSlotMultiplier;
    }

    public static int getMaxExtraHomeSlots() {
        return maxExtraHomeSlots;
    }

    public static int getBuyWarpCost() {
        return buyWarpCost;
    }

    public static double getCreatorWarpCostMultiplier() {
        return creatorWarpCostMultiplier;
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

    public static int getTpaRequestTimeoutSeconds() {
        return tpaRequestTimeoutSeconds;
    }

    public static int getXaeroConfirmTimeoutSeconds() {
        return xaeroConfirmTimeoutSeconds;
    }

    public static int getLeaderboardRefreshSeconds() {
        return leaderboardRefreshSeconds;
    }

    public static boolean isHomeDistanceBased() {
        return homeDistanceBased;
    }

    public static boolean isBackDistanceBased() {
        return backDistanceBased;
    }

    public static boolean isDeathBackDistanceBased() {
        return deathBackDistanceBased;
    }

    public static int getCooldownSeconds(String command) {
        return cooldowns.getOrDefault(command.toLowerCase(), cooldownSeconds);
    }

    public static int getFlatCost(String command) {
        return (int) Math.round(costs.getOrDefault(command.toLowerCase(), 10) * globalCostMultiplier);
    }
}
