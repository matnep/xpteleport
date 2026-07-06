# XPTeleport - Server-Side XP Teleportation Mod

A lightweight, server-side-only Minecraft teleportation mod built for **NeoForge 1.21.1**. It acts as a complete server-side replacement for standard essentials mods without requiring patch work or client-side mods.

This mod introduces distance-based experience (XP) costs, click-to-teleport waypoint interceptions, stand-still anti-abuse warmup checks, OP bypasses, and player-to-player XP level trading.

---

## 🛠️ Features

*   **100% Server-Side:** Players can connect using vanilla clients; no mods are required on the player's computer.
*   **Distance-Based XP Cost (Logarithmic):** Uses a dynamic logarithmic curve calibrated to charge **5 levels per 100 blocks**, capping at 30 levels at the world border.
*   **Anti-Abuse Warmup:** Teleportation requires players to stand still and take no combat damage for 3 seconds, displaying a countdown on their action bar HUD.
*   **Zero Cooldowns by Default:** Continuously teleport without command delay (fully configurable).
*   **Xaero's Minimap Waypoint Integration:** Intercepts shared waypoints in player chat and appends a clickable gold `[Teleport]` button.
*   **Vanilla `/tp` Redirection:** Intercepts coordinate-based tp requests from players and redirects them to a prompt showing the XP cost and requiring a `[CONFIRM]` click.
*   **OP Exemption:** Operators (OP level 2+) pay 0 XP levels and teleport instantly (bypassing warmup checks).
*   **XP Trading Commands:** `/xpgive` and `/xprequest` allow players to safely trade or request XP levels directly from their bars.

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

The config file is generated automatically on launch with help comments explaining each setting:

```json
{
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
  "messages": {
    "insufficient_xp": "§cYou do not have enough XP levels to teleport! (Cost: %s levels, Current: %s levels)",
    "xp_deducted": "§aTeleportation successful! Deducted %s XP levels.",
    "confirm_prompt": "§eYou are about to teleport to [%s, %s, %s] for %s XP levels."
  },
  "redirect_tp_to_xtp": true,
  "max_homes": 5,
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
  "show_ops_on_leaderboard": false
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

### Teleportation
*   **`/tpa <player>`** / **`/tpahere <player>`**: Coordinate requests to/from players.
*   **`/tpaccept`** / **`/tpdeny`** / **`/tpcancel`**: Accept, deny, or cancel active TPA requests.
*   **`/sethome [name]`** / **`/home [name]`** / **`/delhome [name]`** / **`/listhomes`**: Personal warp points.
*   **`/warp <name>`** / **`/listwarps`** / **`/setwarp <name>`** / **`/delwarp <name>`**: Global server warps.
*   **`/spawn`** / **`/back`**: Basic utilities.
*   **`/rtp`** *(alias: `/wild`)*: Teleports to random safe coordinates clamped to the active World Border.
*   **`/xtp <coords>`** / **`/xtp confirm`**: Custom coordinates confirmation endpoint.

### Administration
*   **`/xpteleport info`**: Shows credits. (Usable by all players).
*   **`/xpteleport reload`**: Reloads configuration and warps from disk. (Requires OP level 2).
*   **`/xpteleport change`**: Toggles standard `/tp` redirection. (Requires OP level 2).
*   **`/xpteleport help`**: Displays all available commands. (Usable by all players).
