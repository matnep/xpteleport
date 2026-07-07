# XPTeleport - Server-Side XP Teleportation Mod

A lightweight, server-side-only Minecraft teleportation mod built for **NeoForge 1.21.1**. It acts as a complete server-side replacement for standard essentials mods without requiring patch work or client-side mods.

This mod introduces distance-based experience (XP) costs, click-to-teleport waypoint interceptions, stand-still anti-abuse warmup checks, OP bypasses, and player-to-player XP level trading.

---

## 🛠️ Features

*   **100% Server-Side:** Players can connect using vanilla clients; no mods are required on the player's computer.
*   **Distance-Based XP Cost (Logarithmic):** Uses a dynamic logarithmic curve calibrated to charge **5 levels per 100 blocks**, capping at 30 levels at the world border.
*   **XP-Based Home Slot Purchasing:** Purchase extra home slots using `/buyhome` once reaching default limits, with configurable exponential cost scaling.
*   **Community Warp Creation:** Purchase global warps using `/buywarp <name>`. Creator owns the warp, gets free/discounted teleports, and is the only player (aside from OPs) who can delete it.
*   **Anti-Abuse Warmup:** Teleportation requires players to stand still and take no combat damage for 3 seconds, displaying a countdown on their action bar HUD.
*   **Zero Cooldowns by Default:** Continuously teleport without command delay (fully configurable).
*   **Xaero's Minimap Waypoint Integration:** Intercepts shared waypoints in player chat and appends a clickable gold `[Teleport]` button.
*   **Vanilla `/tp` Redirection:** Intercepts coordinate-based tp requests from players and redirects them to a prompt showing the XP cost and requiring a `[CONFIRM]` click.
*   **OP Exemption:** Operators (OP level 2+) pay 0 XP levels and teleport instantly (bypassing warmup checks).
*   **XP Trading Commands:** `/xpgive` and `/xprequest` allow players to safely trade or request XP levels directly from their bars.
*   **Lag-Free Asynchronous Safe RTP:** Redesigned `/rtp` (or `/wild`) chunk searches to run asynchronously. Safely inspects up to 15 chunks in the background without main thread server lag. Checks for solid ground and 2 blocks of air headspace, avoiding lava, water, magma, fire, and cactus.

---

## 💾 File and Storage Architecture

To keep files organized and easy to back up, the mod saves files in distinct structures:

### 🏠 Player Homes
*   **Path:** `<your_world_save_folder>/xpteleport/homes/<player-uuid>.json`
*   **Purpose:** Player-specific locations set using `/sethome`. By keeping them inside the world save folder, your players' homes are automatically backed up when copying or backing up the world, and reset cleanly if you change worlds.

### 📍 Server Warps
*   **Path:** `config/xpteleport/warps.json`
*   **Purpose:** Global warps set by server administrators. Keeping them inside the `config/` directory ensures warps survive even if the active world save is wiped or changed.

### ⚙️ Main Configuration File
*   **Path:** `config/xpteleport.json`
*   **Purpose:** Holds all customizable settings, messages, and mathematical coefficients. Migrates automatically on launch.

---

## ⚙️ Configuration (`config/xpteleport.json`)

The config file is generated automatically on launch with detailed comments placed directly above each option:

```json
{
  "//_global_cost_multiplier": "Master scale factor for all XP costs (teleports and buys). 2.0 = double, 0.5 = half.",
  "global_cost_multiplier": 1.0,
  "//_costs": "Flat rates (in XP levels) charged for commands when distance_based_xp is set to false.",
  "costs": {
    "tpa": 10,
    "tpahere": 10,
    "tpaccept": 10,
    "home": 10,
    "warp": 10,
    "spawn": 10,
    "rtp": 10,
    "wild": 10,
    "back": 10,
    "xaero_tp": 30
  },
  "//_messages": "Customizable chat translation strings.",
  "messages": {
    "insufficient_xp": "§cYou do not have enough XP levels to teleport! (Cost: %s levels, Current: %s levels)",
    "xp_deducted": "§aTeleportation successful! Deducted %s XP levels.",
    "confirm_prompt": "§eYou are about to teleport to [%s, %s, %s] for %s XP levels."
  },
  "//_redirect_tp_to_xtp": "Redirects non-OP /tp coordinate clicks to map confirm dialog.",
  "redirect_tp_to_xtp": true,
  "//_max_homes": "Default home slots limit a player starts with before upgrades.",
  "max_homes": 5,
  "//_buy_home_slot_cost": "Base XP cost to purchase 1 extra home slot above starting limit.",
  "buy_home_slot_cost": 15,
  "//_buy_home_slot_multiplier": "Multiplier for subsequent home slot purchases (e.g. 1.5). Set to 1.0 for flat price.",
  "buy_home_slot_multiplier": 1.0,
  "//_max_extra_home_slots": "Max extra home slots a player can buy.",
  "max_extra_home_slots": 5,
  "//_buy_warp_cost": "XP cost for players to buy a global warp. Only the creator or OPs can delete it.",
  "buy_warp_cost": 50,
  "//_creator_warp_cost_multiplier": "Cost multiplier applied when teleporting to a warp you created. Set to 0.0 for free of charge, or 0.5 for half price.",
  "creator_warp_cost_multiplier": 0.0,
  "//_rtp_range": "Minimum and maximum block radius search range around spawn on /rtp.",
  "rtp_min_range": 50,
  "rtp_max_range": 500,
  "//_distance_based_xp": "Calculate XP cost dynamically based on distance. If false, falls back to flat rates in 'costs'.",
  "distance_based_xp": true,
  "//_use_logarithmic_cost": "Use logarithmic distance scaling. If true, uses logarithmic formula (100 blocks = 5 levels). If false, uses blocks_per_level.",
  "use_logarithmic_cost": true,
  "//_log_formula": "Formula parameters: cost = base + multiplier * ln(distance). Calibrated to 100 blocks = 5 levels.",
  "base_teleport_cost": -19.3,
  "log_multiplier": 5.28,
  "//_xaero_multiplier": "Cost multiplier applied to Xaero map clicks and shared chat waypoints (e.g. 3.0 = triple cost).",
  "xaero_multiplier": 3.0,
  "//_blocks_per_level": "Linear cost scaling divisor (used if use_logarithmic_cost is false).",
  "blocks_per_level": 500,
  "//_cross_dimension_cost": "Flat XP level cost charged for teleports across dimensions.",
  "cross_dimension_cost": 15,
  "//_warmup_seconds": "Delay in seconds where a player must stand still and take no damage before teleporting.",
  "warmup_seconds": 3,
  "//_cooldown_seconds": "Delay in seconds between successful teleports. Set to 0 to disable cooldowns.",
  "cooldown_seconds": 0,
  "//_show_ops_on_leaderboard": "Set to true to show server operators (OPs) on the XP leaderboard.",
  "show_ops_on_leaderboard": false,
  "//_tpa_request_timeout_seconds": "Time in seconds before a pending TPA request expires.",
  "tpa_request_timeout_seconds": 60,
  "//_xaero_confirm_timeout_seconds": "Time in seconds before a pending Xaero coordinate teleport confirmation expires.",
  "xaero_confirm_timeout_seconds": 30,
  "//_leaderboard_refresh_seconds": "Time in seconds between automatic background refreshes of the XP leaderboard.",
  "leaderboard_refresh_seconds": 300
}
```

---

## 💬 Commands Reference

### XP Leaderboard
*   **`/xp leaderboard`** *(alias: `/xpteleport leaderboard`)*: Displays a chat leaderboard showing the top 10 players on the server ranked by total experience points. (By default, OP players are hidden unless `"show_ops_on_leaderboard": true` is enabled in configuration).

### XP Trading
*   **`/xpgive <player> <levels>`** *(alias: `/xpg`)*: Gives XP levels from your bar to another player. (OPs can give infinite levels without losing XP).
*   **`/xprequest <player> <levels>`** *(alias: `/xpr`)*: Sends a request to another player. They can click `[ACCEPT]` or `[DENY]` in chat to process the trade.
*   **`/xprequest accept`** / **`/xprequest deny`** *(aliases: `/xpr accept` / `/xpr deny`)*: Accept/Deny active XP request.

### XP-Based Purchasing
*   **`/buyhome`**: Purchase 1 extra home slot using XP levels. Cost increases dynamically based on how many slots you have already bought, capped at `max_extra_home_slots`.
*   **`/buywarp <name>`**: Spend a premium fee of XP levels to create a global community warp. The warp is owned by you. Only you (or OPs) can delete it, and you get to teleport to your warp for free (configurable).

### Teleportation
*   **`/tpa <player>`** / **`/tpahere <player>`**: Coordinate requests to/from players.
*   **`/tpaccept`** / **`/tpdeny`** / **`/tpcancel`**: Accept, deny, or cancel active TPA requests.
*   **`/sethome [name]`** / **`/home [name]`** / **`/delhome [name]`** / **`/listhomes`**: Personal warp points. Obeys standard limits plus purchased home slots.
*   **`/warp <name>`** / **`/listwarps`** / **`/setwarp <name>`** / **`/delwarp <name>`**: Global server warps. Standard `/delwarp` is only usable by OPs or the creator of the warp.
*   **`/spawn`** / **`/back`**: Basic utilities.
*   **`/rtp`** *(alias: `/wild`)*: Runs a **100% Asynchronous safe terrain search** in the background, checking up to 15 locations before finding solid ground to teleport. Completely lag-free.
*   **`/xtp <coords>`** / **`/xtp confirm`**: Custom coordinates confirmation endpoint.

### Administration
*   **`/xpteleport info`**: Shows credits. (Usable by all players).
*   **`/xpteleport reload`**: Reloads configuration and warps from disk. (Requires OP level 2).
*   **`/xpteleport change`**: Toggles standard `/tp` redirection. (Requires OP level 2).
*   **`/xpteleport help`**: Displays all available commands. (Usable by all players).
