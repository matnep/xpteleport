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
  "global_cost_multiplier": 1.0,
  "costs": {
    "tpa": 10,
    "tpahere": 10,
    "tpaccept": 10,
    "home": 2,
    "warp": 10,
    "spawn": 10,
    "rtp": 10,
    "wild": 10,
    "back": 10,
    "death_back": 2,
    "xaero_tp": 30
  },
  "messages": {
    "insufficient_xp": "§cYou do not have enough XP levels to teleport! (Cost: %s levels, Current: %s levels)",
    "xp_deducted": "§aTeleportation successful! Deducted %s XP levels.",
    "confirm_prompt": "§eYou are about to teleport to [%s, %s, %s] for %s XP levels."
  },
  "redirect_tp_to_xtp": true,
  "max_homes": 5,
  "buy_home_slot_cost": 15,
  "buy_home_slot_multiplier": 1.0,
  "max_extra_home_slots": 5,
  "buy_warp_cost": 50,
  "creator_warp_cost_multiplier": 0.0,
  "rtp_min_range": 50,
  "rtp_max_range": 500,
  "distance_based_xp": true,
  "use_logarithmic_cost": true,
  "base_teleport_cost": -19.3,
  "log_multiplier": 5.28,
  "xaero_multiplier": 3.0,
  "blocks_per_level": 500,
  "cross_dimension_cost": 15,
  "warmup_seconds": 3,
  "cooldown_seconds": 0,
  "show_ops_on_leaderboard": false,
  "tpa_request_timeout_seconds": 60,
  "xaero_confirm_timeout_seconds": 30,
  "leaderboard_refresh_seconds": 300,
  "home_distance_based": false,
  "back_distance_based": true,
  "death_back_distance_based": false,
  "cooldowns": {
    "tpa": 0,
    "tpahere": 0,
    "tpaccept": 0,
    "home": 60,
    "warp": 0,
    "spawn": 0,
    "rtp": 120,
    "wild": 120,
    "back": 0,
    "death_back": 0
  }
}
```

---

## 💬 Commands Reference

### XP Leaderboard
*   **`/xpteleport leaderboard`**: Displays a chat leaderboard showing the top 10 players on the server ranked by XP levels. Output is formatted as a clean plain-text list (`rank. PlayerName levelslevels`) with no color codes or total XP.

### XP Trading
*   **`/xpgive <player> <levels>`** *(alias: `/xpg`)*: Gives XP levels from your bar to another player. (OPs can give infinite levels without losing XP).
*   **`/xprequest <player> <levels>`** *(alias: `/xpr`)*: Sends a request to another player. They can click `[ACCEPT]` or `[DENY]` in chat to process the trade.
*   **`/xprequest accept`** / **`/xprequest deny`** *(aliases: `/xpr accept` / `/xpr deny`)*: Accept/Deny active XP request.

### XP-Based Purchasing
*   **`/buyhome`**: Purchase 1 extra home slot using XP levels. Cost increases dynamically based on how many slots you have already bought, capped at `max_extra_home_slots`.
*   **`/buywarp <name>`**: Spend a premium fee of XP levels to create a global community warp. The warp is owned by you. Only you (or OPs) can delete it, and you get to teleport to your warp for free (configurable).

### Teleportation
*   **`/tpa <player>`**: Request to teleport to a player's location.
*   **`/tpahere <player>`**: Request a player to teleport to your location. When they accept, you (the summoning requester) pay the XP cost, and they perform the stand-still warmup.
*   **`/tpaccept`** / **`/tpdeny`** / **`/tpcancel`**: Accept, deny, or cancel active TPA requests.
*   **`/sethome [name]`** / **`/home [name]`** / **`/delhome [name]`** / **`/listhomes`**: Personal warp points. Obeys standard limits plus purchased home slots. Charges a flat rate of 2 XP levels by default.
*   **`/warp <name>`** / **`/listwarps`** / **`/setwarp <name>`** / **`/delwarp <name>`**: Global server warps. Standard `/delwarp` is only usable by OPs or the creator of the warp.
*   **`/spawn`** / **`/back`**: Basic utilities. Going `/back` after death charges a flat rate of 2 XP levels by default. Regular `/back` charges based on distance.
*   **`/rtp`** *(alias: `/wild`)*: Runs a **100% Asynchronous safe terrain search** in the background, checking up to 15 locations before finding solid ground to teleport. Completely lag-free. Charges a flat rate of 10 XP levels.
*   **`/xtp <coords>`** / **`/xtp confirm`**: Custom coordinates confirmation endpoint.

### Administration
*   **`/xpteleport info`**: Shows credits. (Usable by all players).
*   **`/xpteleport reload`**: Reloads configuration and warps from disk. (Requires OP level 2).
*   **`/xpteleport change`**: Toggles standard `/tp` redirection. (Requires OP level 2).
*   **`/xpteleport help`**: Displays all available commands. (Usable by all players).
