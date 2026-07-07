# Changelog

All notable changes to the **XPTeleport** mod will be documented in this file.

---

## [1.0.0-beta] - 2026-07-07

This is the initial beta release of **XPTeleport**, a 100% standalone, server-side teleportation mod for **NeoForge 1.21.1**. It serves as a complete replacement for standard essentials teleportation mods (like FTB Essentials), running completely on the server side so vanilla clients can connect without any mod installations.

### Added

#### 🚀 Core & Standalone Architecture
- **Standalone NeoForge 1.21.1 Native Mod:** Removed all external dependencies on FTB Essentials, FTB Library, and Architectury.
- **Server-Side Only:** No client-side installation required. Players can join using a standard vanilla Minecraft client.
- **Separated Data Storage:**
  - **Player Homes:** Saved in `<world>/xpteleport/homes/<player-uuid>.json`. This ensures player home data is bundled with world saves for easy backups and resets cleanly with a new world.
  - **Global Warps:** Saved in the global config folder at `config/xpteleport/warps.json`, ensuring warp points persist even if the world is reset.

#### 💎 Balanced XP Economy & Mechanics
- **Logarithmic Distance-Based XP Costs:** Teleportation cost scales dynamically with distance ($d$) based on the formula: $\text{Cost} = -19.3 + 5.28 \times \ln(d)$ (calibrated to exactly 5 levels per 100 blocks, capping at 30 levels at the world border).
- **Cross-Dimension Flat Cost:** Teleporting between dimensions (e.g. Overworld to Nether/End) charges a flat, configurable fee of 15 XP levels.
- **Xaero's Waypoint Multiplier:** Waypoint-based teleports apply a configurable multiplier (default `3.0x`).
- **OP Exemptions:** Operators (OP level 2+) pay 0 XP levels for all teleportation.

#### 🛡️ Anti-Abuse & Combat Log Checks
- **Action Bar Warmup Timer:** Teleport commands trigger a 3-second warmup. A ticking timer is displayed on the player's HUD action bar.
- **Movement & Damage Interception:** Warmup is instantly canceled if the player moves or takes combat damage (monitored via `LivingIncomingDamageEvent`).
- **OP Warmup Bypass:** Operators bypass warmup times entirely, teleporting instantly.

#### 👥 Player Interaction & Commands
- **Interactive XP Trading:**
  - `/xpgive <player> <levels>` *(alias: `/xpg`)*: Safely transfers XP levels to another player. OPs can give unlimited levels without losing their own XP.
  - `/xprequest <player> <levels>` *(alias: `/xpr`)*: Sends a transfer request. The recipient receives interactive clickable green `[ACCEPT]` and red `[DENY]` buttons in chat.
- **Minimap Waypoint Interception:** Intercepts shared waypoints matching the Xaero's Minimap pattern in player chat and appends a clickable gold `[Teleport]` button.
- **Vanilla `/tp` Command Redirection:** Coordinate-based `/tp` requests from players are intercepted and redirected to a custom `/xtp` confirmation prompt showcasing the XP level cost.

#### ⚡ Performance Optimizations
- **Lag-Free RTP (Random Teleport):**
  - Search boundaries are clamped to the active **`WorldBorder`** with a 16-block safety buffer.
  - Chunk search retry count reduced to **3 attempts** to prevent synchronous chunk generation lag on the main server thread.
  - Default maximum range updated to **500 blocks** to target pre-generated chunks.
- **Asynchronous XP Leaderboard (`/xp leaderboard`):**
  - Queries offline player NBT files (`XpTotal` and `XpLevel`) asynchronously on a background thread (`CompletableFuture`) to prevent I/O disk reads from freezing the server main thread.
  - Results are cached and refreshed every 5 minutes.
  - Operators are hidden by default, customizable via the `"show_ops_on_leaderboard": false` config setting.

#### ⚙️ Configuration & Tooling
- **Automatic Config Migrator:** Automatically loads existing configs, upgrades deprecated keys, and injects helpful help description comments to avoid losing custom settings.
- **`/xpteleport help` Command:** Interactive colored reference command guide.
- **Development & Repository setup:** Initialized clean repository structure with `.gitignore` and pushed to **[github.com/matnep/xpteleport](https://github.com/matnep/xpteleport)**.
