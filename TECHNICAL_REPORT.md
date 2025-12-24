# BOCRacingV2 — Current State Technical Report

## 1. Project Overview

**Plugin Name:** BOCRacingV2  
**Minecraft/Paper Version Target:** Paper API 1.21.10-R0.1-SNAPSHOT  
**Java Version:** Java 21  
**Purpose:** Race course management plugin for boat racing and air racing with admin setup workflow, button-driven player interaction, and per-course start mode configurations (CROSS_LINE and DROP_START).

---

## 2. Runtime Architecture

### Runtime Managers

#### 1. SetupSessionManager (`com.bocrace.setup.SetupSessionManager`)
- **State Held:** `Map<UUID, SetupSession>` (in-memory)
  - Tracks per-admin setup sessions (armed action, pending region points)
- **Persistence:** In-memory only
- **Initialization:** Created in `onEnable()` in `BOCRacingV2.java`
- **Cleanup:** `clearAll()` called in `onDisable()`

#### 2. CourseManager (`com.bocrace.storage.CourseManager`)
- **State Held:** None (stateless, operates on YAML files)
  - Manages YAML persistence in `plugins/BOCRacingV2/boatracing/` and `plugins/BOCRacingV2/airracing/`
- **Persistence:** YAML files on disk
- **Initialization:** Created in `onEnable()` in `BOCRacingV2.java`
- **Cleanup:** None required (no persistent state)

#### 3. RaceManager (`com.bocrace.runtime.RaceManager`)
- **State Held:** All in-memory
  - `Map<CourseKey, SoloLock> activeSoloLocks` — Tracks active solo course locks (expire after cooldown)
  - `Map<CourseKey, MultiLobbyState> activeMultiLobbies` — Tracks multiplayer lobby states
  - `Map<CourseKey, Map<UUID, ActiveRun>> activeRuns` — Tracks active race runs per course per player
  - `ActiveRun` fields: `nextRequiredCheckpointIndex` (int, 1-based), `passedCheckpoints` (Set<Integer>), `checkpointSplitTimes` (Map<Integer, Long>), `lastCheckpointMessageMillis` (long, for anti-spam)
- **Persistence:** In-memory only
- **Initialization:** Created in `onEnable()` in `BOCRacingV2.java`
- **Cleanup:** `clearAll()` called in `onDisable()`

#### 4. DropBlockManager (`com.bocrace.runtime.DropBlockManager`)
- **State Held:** `Map<CourseKey, List<DropTask>> activeDropTasks` (in-memory)
  - Each DropTask stores: worldName, Map<Location, BlockState>, scheduled taskId
- **Persistence:** In-memory only
- **Initialization:** Created in `onEnable()` in `BOCRacingV2.java`
- **Cleanup:** `clearAll()` cancels all scheduled restoration tasks and restores blocks, called in `onDisable()`

### Scheduled Tasks

#### 1. RaceDetectionTask (`com.bocrace.runtime.RaceDetectionTask`)
- **Type:** Repeating BukkitRunnable
- **Schedule:** Runs every 5 ticks (0.25 seconds)
- **Purpose:** Detects player location inside start/finish regions for CROSS_LINE starts, checkpoint crossings (if required), finish detection, and HUD updates
- **Initialization:** Started in `onEnable()` via `runTaskTimer(this, 0L, 5L)`
- **Cleanup:** Task cancelled in `onDisable()`

#### 2. Drop Block Restoration Tasks
- **Type:** Delayed BukkitRunnable (one per racer in DROP_START mode)
- **Schedule:** Runs once after `settings.drop.restoreSeconds * 20` ticks
- **Purpose:** Restores blocks to original state after drop period
- **Created:** When `DropBlockManager.dropBlocks()` is called at GO for DROP_START
- **Cleanup:** Cancelled via `cancelAllDrops()` or when task completes naturally

#### 3. Countdown Tasks
- **Type:** Repeating BukkitRunnable (one per race start)
- **Schedule:** Runs every 20 ticks (1 second) for `settings.countdownSeconds` iterations
- **Purpose:** Displays countdown messages (5...4...3...2...1...GO!) before race start
- **Created:** In `handleSoloJoin()` and `handleMpLeaderStart()` in `CourseButtonListener`
- **Cleanup:** Self-cancels after GO message

### Listeners

#### 1. SetupListener (`com.bocrace.listener.SetupListener`)
- **Event:** `PlayerInteractEvent` (priority HIGH)
- **Purpose:** Captures right-click blocks during admin setup for locations/regions/buttons
- **Registered:** `onEnable()` in `BOCRacingV2.java`

#### 2. CourseButtonListener (`com.bocrace.listener.CourseButtonListener`)
- **Event:** `PlayerInteractEvent` (priority HIGH)
- **Purpose:** Handles player right-clicks on course buttons (solo join/return, MP join/start/cancel/create)
- **Registered:** `onEnable()` in `BOCRacingV2.java`

#### 3. PlayerLifecycleListener (`com.bocrace.listener.PlayerLifecycleListener`)
- **Events:** `PlayerQuitEvent`, `PlayerKickEvent` (priority MONITOR)
- **Purpose:** Cleans up solo locks, active runs, and MP lobby state on player disconnect
- **Registered:** `onEnable()` in `BOCRacingV2.java`

---

## 3. Course Model & Config

### Course Class Structure

**Package:** `com.bocrace.model.Course`

**Top-Level Fields:**
- `String name` — Course display name
- `CourseType type` — Enum: BOAT or AIR
- `CourseSettings settings` — Nested settings object (defaults applied on construction)
- `Location courseLobbySpawn` — Single lobby spawn location (nullable)
- `List<Location> playerSpawns` — List of player spawn locations (initialized as empty ArrayList)
- `VolumeRegion startRegion` — Start line region (nullable)
- `VolumeRegion finishRegion` — Finish line region (nullable)
- `List<CheckpointRegion> checkpoints` — List of checkpoint regions (initialized as empty ArrayList)
- `BlockCoord soloJoinButton` — Solo join button block coordinate (nullable)
- `BlockCoord soloReturnButton` — Solo return button block coordinate (nullable)
- `Location mpLobby` — Multiplayer lobby return location (nullable)
- `BlockCoord mpJoinButton` — Multiplayer join button block coordinate (nullable)
- `BlockCoord mpLeaderCreateButton` — MP leader create button block coordinate (nullable)
- `BlockCoord mpLeaderStartButton` — MP leader start button block coordinate (nullable)
- `BlockCoord mpLeaderCancelButton` — MP leader cancel button block coordinate (nullable)

### Enums

#### 1. CourseType (`com.bocrace.model.CourseType`)
- Values: `BOAT`, `AIR`

#### 2. StartMode (nested in `Course`)
- Values: `CROSS_LINE`, `DROP_START`

#### 3. DropShape (nested in `Course.DropSettings`)
- Values: `SINGLE`, `SQUARE`, `CIRCLE`

### Nested Classes

#### 1. CourseSettings (nested in `Course`)
- **Fields:**
  - `StartMode startMode = StartMode.CROSS_LINE` (default)
  - `int countdownSeconds = 5` (default)
  - `int soloCooldownSeconds = 120` (default)
  - `DropSettings drop = new DropSettings()` (default)
  - `RulesSettings rules = new RulesSettings()` (default)

#### 2. DropSettings (nested in `Course`)
- **Fields:**
  - `DropShape shape = DropShape.SINGLE` (default)
  - `int radius = 1` (default)
  - `int restoreSeconds = 10` (default)

#### 3. RulesSettings (nested in `Course`)
- **Fields:**
  - `boolean requireCheckpoints = false` (default)

#### 4. BlockCoord (nested in `Course`)
- **Fields:** `String world`, `int x`, `int y`, `int z`

#### 5. VolumeRegion (nested in `Course`)
- **Fields:** `String world`, `BlockCoord min`, `BlockCoord max`

#### 6. CheckpointRegion (nested in `Course`)
- **Fields:** `int checkpointIndex`, `BlockCoord point1`, `BlockCoord point2`

### YAML Structure

**File Location:**
- BOAT courses: `plugins/BOCRacingV2/boatracing/<safeFileName>.yml`
- AIR courses: `plugins/BOCRacingV2/airracing/<safeFileName>.yml`

**Safe File Name Rules:**
- Spaces replaced with underscores
- Non-alphanumeric characters (except `_` and `-`) removed
- Original `displayName` preserved in YAML

**YAML Comment Header (written by `saveCourseWithComments()`):**
```yaml
# BOCRacingV2 Course Configuration
# Generated automatically - edit with care

# === START MODE SETTINGS ===
# startMode options:
#   CROSS_LINE = countdown runs; timer starts ONLY on crossing start trigger
#                 (boat start volume / air start volume)
#   DROP_START = countdown runs; timer starts at GO; blocks under racers drop briefly
#
# DROP_START shape options:
#   SINGLE = drop only the block directly under the racer's spawn
#   SQUARE = drop blocks in a square pattern (radius x radius) at spawn Y level
#   CIRCLE = drop blocks in a circular pattern (radius) at spawn Y level
# radius is only used for SQUARE and CIRCLE shapes

# === CHECKPOINTS ===
# Checkpoints are optional unless settings.rules.requireCheckpoints=true
# If required, you must have at least 1 checkpoint with sequential indexing

# === COURSE DATA ===
```

**YAML Data Structure:**
```yaml
displayName: <original name>
fileName: <safe file name>
type: BOAT|AIR
settings:
  startMode: CROSS_LINE|DROP_START
  countdownSeconds: <int>
  soloCooldownSeconds: <int>
  drop:
    shape: SINGLE|SQUARE|CIRCLE
    radius: <int>
    restoreSeconds: <int>
  rules:
    requireCheckpoints: true|false
courseLobby:
  world: <world name>
  x: <double>
  y: <double>
  z: <double>
  yaw: <float>
  pitch: <float>
playerSpawns:
  0:
    world: <world name>
    x: <double>
    y: <double>
    z: <double>
    yaw: <float>
    pitch: <float>
  # ... more spawns indexed by integer key
start:
  world: <world name>
  min:
    x: <int>
    y: <int>
    z: <int>
  max:
    x: <int>
    y: <int>
    z: <int>
finish:
  world: <world name>
  min:
    x: <int>
    y: <int>
    z: <int>
  max:
    x: <int>
    y: <int>
    z: <int>
checkpoints:
  0:
    index: <int>
    point1:
      world: <world name>
      x: <int>
      y: <int>
      z: <int>
    point2:
      world: <world name>
      x: <int>
      y: <int>
      z: <int>
  # ... more checkpoints indexed by integer key
soloJoinButton:
  world: <world name>
  x: <int>
  y: <int>
  z: <int>
soloReturnButton:
  # ... (same structure, optional)
mpLobby:
  world: <world name>
  x: <double>
  y: <double>
  z: <double>
  yaw: <float>
  pitch: <float>
mpJoinButton:
  # ... (same BlockCoord structure)
mpLeaderCreateButton:
  # ... (same BlockCoord structure, optional)
mpLeaderStartButton:
  # ... (same BlockCoord structure)
mpLeaderCancelButton:
  # ... (same BlockCoord structure)
```

**Field Requirements:**
- **Required:** `displayName`, `fileName`, `type`, `settings` (auto-filled with defaults if missing)
- **Optional:** All other fields (nullable or empty list)
- **Settings auto-migration:** If `settings.startMode` is missing on load, defaults are applied and file is auto-saved once

**Auto-Migration Behavior:**
1. **Start/Finish region migration:** Old format (`start.point1`, `start.point2`) is migrated to new format (`start.min`, `start.max`) on load, with BOAT logic (ground volume, height=2). File is auto-saved after migration.
2. **Settings migration:** Missing settings are filled with defaults, file is auto-saved if `settings.startMode` was missing.

---

## 4. Start Modes (FACTUAL)

### CROSS_LINE Mode Lifecycle

#### SOLO Flow:
1. Player clicks `solo_join_button`
2. Validation: Course must have exactly 1 spawn and `soloJoinButton` exists
3. Lock check: If course is locked (non-expired SoloLock), player receives "Course in use. Try again in X seconds."
4. If unlocked: SoloLock acquired for `settings.soloCooldownSeconds` (default 120)
5. Player teleported to spawn[0]
6. ActiveRun created with spawnIndex=0
7. Countdown starts: Runs for `settings.countdownSeconds` (default 5) seconds, displays "5...4...3...2...1..." via chat message every 20 ticks
8. At GO (countdown == 0): Player receives "§a§lGO!" chat message, then "§7Cross the start line to begin!" chat message. Timer does NOT start yet.
9. Start detection: `RaceDetectionTask` checks every 5 ticks if player location is inside `startRegion`. When detected, `ActiveRun.setStartMillis(System.currentTimeMillis())` is called, `started` flag set to true, player receives "§aTimer started!" chat message.
10. Finish detection: `RaceDetectionTask` checks every 5 ticks if player location is inside `finishRegion` (only if `started==true` and `finished==false`). When detected, `ActiveRun.setFinishMillis(System.currentTimeMillis())` is called, `finished` flag set to true, player receives "§a§lFinished! Time: §f<mm:ss.SSS>" chat message. Solo lock released, ActiveRun removed.
11. Cleanup: Solo lock and ActiveRun cleared on finish (or on quit/kick, or when player clicks `solo_return_button`).

#### MULTIPLAYER Flow:
1. Leader clicks `mp_leader_start_button`
2. Validation: Course must have `mpLeaderStartButton` exists, 2+ player spawns, lobby state == OPEN, leader is caller, 2+ total players joined
3. Lobby state set to STARTING
4. ActiveRun created for each racer in lobby (including leader if not yet assigned)
5. Countdown starts: Runs for `settings.countdownSeconds` seconds, displays "5...4...3...2...1..." to all racers via chat message every 20 ticks
6. At GO: Lobby state set to IN_PROGRESS, all racers receive "§a§lGO!" chat message, then "§7Cross the start line to begin!" chat message. Timers do NOT start yet.
7. Start detection: Per-racer, `RaceDetectionTask` checks every 5 ticks if player location is inside `startRegion`. When detected for a racer, that racer's `ActiveRun.setStartMillis()` is called, `started` flag set to true, `nextRequiredCheckpointIndex` initialized to 1, that racer receives "§aTimer started!" chat message. Each racer's timer starts independently when they cross.
8. Checkpoint detection (if `requireCheckpoints==true`): Per-racer, `RaceDetectionTask` checks every 5 ticks. Each racer progresses through checkpoints independently. See SOLO flow for checkpoint behavior details.
9. HUD update: Per-racer, `RaceDetectionTask` updates ActionBar every 5 ticks showing elapsed time and checkpoint progress.
10. Finish detection: Per-racer, `RaceDetectionTask` checks every 5 ticks if player location is inside `finishRegion` (only if `started==true` and `finished==false`). Finish enforcement applies per-racer (must complete all required checkpoints). When detected, that racer's `ActiveRun.setFinishMillis()` is called, `finished` flag set to true, that racer receives "§a§lFinished! Time: §f<mm:ss.SSS>" chat message.
9. All-finish cleanup: When all racers in lobby have `finished==true`, `RaceManager.clearActiveRuns()` and `RaceManager.clearMultiLobby()` are called. Lobby state cleared.

### DROP_START Mode Lifecycle

#### SOLO Flow:
1. Steps 1-7 identical to CROSS_LINE (lock check, teleport, countdown)
2. At GO: Player receives "§a§lGO!" chat message. Timer starts IMMEDIATELY: `ActiveRun.setStartMillis(System.currentTimeMillis())` is called. Blocks are dropped: `DropBlockManager.dropBlocks()` is called with spawn location and `settings.drop`, which:
   - Determines drop center: `spawnY - 1` (block directly under spawn)
   - Based on `drop.shape`:
     - SINGLE: One block at center
     - SQUARE: All blocks in [centerX-radius..centerX+radius] × [centerZ-radius..centerZ+radius] at centerY
     - CIRCLE: Blocks where `(dx² + dz²) <= radius²` at centerY
   - Replaces blocks with `Material.AIR`
   - Stores original `BlockState` for each block in a `DropTask`
   - Schedules restoration task to run after `settings.drop.restoreSeconds * 20` ticks
3. Start detection: None (timer already started at GO)
4. Finish detection: Identical to CROSS_LINE (checks finish region, records finish time, displays time message)
5. Cleanup: Solo lock and ActiveRun cleared on finish. Block restoration: If player quits/clicks return before restore task fires, `DropBlockManager.cancelAllDrops()` restores blocks immediately. Otherwise, scheduled task restores blocks after `restoreSeconds`.

#### MULTIPLAYER Flow:
1. Steps 1-5 identical to CROSS_LINE (leader start, countdown)
2. At GO: Lobby state set to IN_PROGRESS, all racers receive "§a§lGO!" chat message. For each racer: Timer starts IMMEDIATELY: `ActiveRun.setStartMillis(System.currentTimeMillis())`. Blocks dropped: `DropBlockManager.dropBlocks()` is called for each racer's spawn location independently (each racer gets their own drop pattern and restoration task).
3. Start detection: None (all timers already started at GO)
4. Finish detection: Identical to CROSS_LINE (per-racer finish detection, time messages)
5. All-finish cleanup: When all racers finished, lobby and runs cleared. Block restoration: Per-racer restoration tasks run independently. If race cancelled early, `cancelAllDrops()` restores all blocks immediately.

**Countdown Behavior:**
- Duration: `settings.countdownSeconds` (default 5 seconds)
- Interval: 20 ticks (1 second) between messages
- Messages: Chat messages "§6<countdown>..." (e.g., "§65...", "§64...", etc.), followed by "§a§lGO!" at countdown==0
- Sounds: `Sound.BLOCK_NOTE_BLOCK_HAT` played each second during countdown, `Sound.ENTITY_PLAYER_LEVELUP` (pitch 1.5) at GO
- Scope: SOLO sends to single player; MP sends to all racers in lobby
- Task: BukkitRunnable runs every 20 ticks, decrements counter, sends message and sound, self-cancels after GO

**Sounds/Messages:**
- Countdown: Tick sound (`BLOCK_NOTE_BLOCK_HAT`) each second, GO sound (`ENTITY_PLAYER_LEVELUP` at pitch 1.5)
- Chat messages: Countdown numbers, GO message, timer start, checkpoint messages, finish message
- ActionBar: HUD shows elapsed time "mm:ss.SSS" and checkpoint progress "CP: current/total" (if required) every 5 ticks
- Timer start: "§aTimer started!" (CROSS_LINE only, via RaceDetectionTask)
- Checkpoints: "Checkpoint X/Y" (ActionBar, if required), "Wrong checkpoint. Next: #N" (chat, if wrong checkpoint entered)
- Finish: "§a§lFinished! Time: §f<mm:ss.SSS>" (both modes, via RaceDetectionTask)

---

## 5. Drop System (DROP_START)

**Trigger:** Executed at GO in DROP_START mode for each racer (SOLO: one racer, MP: all racers simultaneously)

**Drop Center Calculation:**
- X: `spawnLoc.getBlockX()`
- Y: `spawnLoc.getBlockY() - 1` (block directly under spawn location)
- Z: `spawnLoc.getBlockZ()`
- World: Same as spawn location's world

**Shape Logic:**
1. **SINGLE:**
   - Drops: One block at (centerX, centerY, centerZ)
   - Radius: Ignored

2. **SQUARE:**
   - Drops: All blocks in rectangle [centerX-radius .. centerX+radius] × [centerZ-radius .. centerZ+radius] at Y=centerY
   - Total blocks: `(2*radius + 1)²`
   - Radius: Uses `settings.drop.radius`

3. **CIRCLE:**
   - Drops: Blocks where `(dx² + dz²) <= radius²` within bounds [centerX-radius .. centerX+radius] × [centerZ-radius .. centerZ+radius] at Y=centerY
   - Uses: `settings.drop.radius`

**Block Replacement:**
- Method: `block.setType(Material.AIR)`
- Original state stored: `BlockState` captured before replacement via `block.getState()`
- Storage: Stored in `Map<Location, BlockState>` within `DropTask`

**Restoration:**
- Delay: `settings.drop.restoreSeconds * 20` ticks (default: 10 seconds = 200 ticks)
- Method: Scheduled `BukkitRunnable` calls `BlockState.update(true, false)` for each stored block
- Task storage: Each `DropTask` stores `taskId` of the scheduled restoration task
- Early cancellation: `DropBlockManager.cancelAllDrops(CourseKey)` cancels all scheduled tasks for a course and restores blocks immediately

**Drop Task Tracking:**
- Storage: `Map<CourseKey, List<DropTask>> activeDropTasks` in `DropBlockManager`
- Multiple tasks: In MP, each racer gets their own `DropTask` (one per spawn location)
- Cleanup: Tasks removed from map after restoration completes or after cancellation

---

## 6. Start/Finish/Checkpoint Detection

**Detection Task:** `RaceDetectionTask` (repeating every 5 ticks)

**Region Check Method:** `isInRegion(Location loc, VolumeRegion region)`
- Validates: Player world matches region world, region.min and region.max are non-null
- Calculation: Computes `minX = min(region.min.x, region.max.x)`, `maxX = max(...)`, same for Y and Z
- Check: `playerBlockX >= minX && playerBlockX <= maxX && playerBlockY >= minY && playerBlockY <= maxY && playerBlockZ >= minZ && playerBlockZ <= maxZ`
- Uses: Player location block coordinates (`loc.getBlockX()`, `getBlockY()`, `getBlockZ()`)

**Checkpoint Check Method:** `isInCheckpoint(Location loc, CheckpointRegion checkpoint)`
- Validates: Player world matches checkpoint point1 world, point1 and point2 are non-null
- Calculation: Computes cuboid bounds from point1 and point2 (same logic as VolumeRegion)
- Check: Player block coordinates must be within cuboid bounds (inclusive)
- Uses: Player location block coordinates

**Start Detection (CROSS_LINE only):**
- Condition: `startMode == CROSS_LINE && !run.isStarted() && startRegion != null`
- Action: Calls `run.setStartMillis(System.currentTimeMillis())`, sets `started=true`, initializes `nextRequiredCheckpointIndex=1`, sends "§aTimer started!" to player
- Scope: Per-racer (each racer starts independently)

**Checkpoint Detection (if `requireCheckpoints==true`):**
- Condition: `run.isStarted() && !run.isFinished() && requireCheckpoints==true && checkpoints exist`
- Detection: Checks if player location is inside checkpoint region (cuboid between point1 and point2) matching `nextRequiredCheckpointIndex`
- Correct checkpoint: Marks checkpoint as passed (adds to `passedCheckpoints` set), stores split time in `checkpointSplitTimes` map, advances `nextRequiredCheckpointIndex++`, sends ActionBar message "Checkpoint X/Y" (1s cooldown per racer)
- Wrong checkpoint: Sends chat message "Wrong checkpoint. Next: #N" (1s cooldown per racer), does not advance progression
- Scope: Per-racer (each racer progresses independently)

**Finish Detection (all modes):**
- Condition: `run.isStarted() && !run.isFinished() && finishRegion != null`
- Enforcement: If `requireCheckpoints==true` and `nextRequiredCheckpointIndex <= totalCheckpoints`, finish is blocked with message "Missing checkpoint #N"
- Action (if allowed): Calls `run.setFinishMillis(System.currentTimeMillis())`, sets `finished=true`, calculates elapsed time via `run.getElapsedMillis()`, formats as "mm:ss.SSS", sends "§a§lFinished! Time: §f<time>" to player
- Time calculation: `elapsedMillis = finishMillis - startMillis` (or current time if not finished)
- Format: `String.format("%02d:%02d.%03d", minutes, seconds, milliseconds)`

**HUD Update:**
- Condition: `run.isStarted() && !run.isFinished()`
- Action: Updates ActionBar every 5 ticks with elapsed time "mm:ss.SSS" and checkpoint progress "CP: current/total" (if `requireCheckpoints==true`)

**Region Storage:**
- **BOAT courses:** Start/finish stored as ground volumes (height=2). During setup: `minY = min(cornerA.y, cornerB.y)`, `maxY = minY + 1`. X/Z bounds computed from corner min/max.
- **AIR courses:** Start/finish stored as full 3D cuboids. During setup: `minY = min(cornerA.y, cornerB.y)`, `maxY = max(cornerA.y, cornerB.y)`. X/Z bounds computed from corner min/max.

---

## 7. Cleanup & Edge Cases

### SOLO Cleanup

**On Finish:**
- Actions: `RaceManager.releaseSoloLock(courseKey, racerUuid)`, `RaceManager.removeActiveRun(courseKey, racerUuid)`
- Block drops: Not cancelled automatically on finish. Restoration happens via scheduled task or if player clicks return button.

**On Quit/Kick:**
- Actions: `RaceManager.clearSoloLockIfHeldBy(playerUuid)` (removes all solo locks held by player), `RaceManager.removeActiveRun(key, playerUuid)` (for solo runs only), `DropBlockManager.cancelAllDrops(key)` (immediate block restoration)
- Logic: Identifies solo run by checking if `courseRuns.size() == 1` before cleanup

**On Return Button:**
- Actions: `RaceManager.releaseSoloLock()` (only if player holds it), `RaceManager.removeActiveRun()`, `DropBlockManager.cancelAllDrops()`, player teleported to `courseLobbySpawn`

### MULTIPLAYER Cleanup

**On Cancel Button (Leader):**
- Actions: `DropBlockManager.cancelAllDrops(key)`, all players teleported to `mpLobby` (fallback to `courseLobbySpawn`), `RaceManager.clearActiveRuns(key)`, `RaceManager.clearMultiLobby(key)`
- Access: Leader-only (OP override allowed)

**On Quit/Kick (During Race):**
- Actions: `RaceManager.removeActiveRun(key, playerUuid)`, `RaceManager.removePlayerFromLobby(playerUuid, "Player left")` (frees spawn index, handles leader reassignment)
- Leader reassignment: If leader quits and lobby has other players, first remaining player in `joinedPlayers` keyset becomes new leader, all remaining players notified
- Empty lobby: If lobby becomes empty, `RaceManager.clearMultiLobby(key)` called, `DropBlockManager.cancelAllDrops(key)` called, `RaceManager.clearActiveRuns(key)` called
- All-finish detection: If `lobby.state == IN_PROGRESS` and `activeRuns.isEmpty()`, lobby and runs cleared, drops cancelled

**On All-Finish (Detection Task):**
- Condition: All ActiveRuns for course have `finished==true` and lobby state is IN_PROGRESS
- Actions: `RaceManager.clearActiveRuns(key)`, `RaceManager.clearMultiLobby(key)`
- Block drops: Not explicitly cancelled (scheduled restoration tasks will complete naturally)

**Lobby State Transitions:**
- OPEN: Initial state, players can join
- STARTING: Set when leader clicks start button, countdown running
- IN_PROGRESS: Set at GO, race active, timers running (or waiting for start cross in CROSS_LINE)

---

## 8. Admin Setup Workflow

**Command Structure:** `/bocrace <subcommand> [args]`

**Available Commands:**
1. `/bocrace create <boatrace|airrace> <name>` — Creates new course YAML file immediately
2. `/bocrace setup <name> <action>` — Arms a setup action (requires active session)
3. `/bocrace status <name>` — Displays course setup checklist and settings
4. `/bocrace validate <name>` — Runs validation checks (read-only, no state changes)
5. `/bocrace cancel` — Cancels current armed setup action

**Setup Actions:**
- `player_spawn` — Single-click, adds to list
- `course_lobby` — Single-click, overwrites
- `start` — Two-click region (corner A, then corner B)
- `finish` — Two-click region (corner A, then corner B)
- `checkpoint` — Two-click region (corner A, then corner B), auto-indexed
- `solo_join_button` — Single-click block coordinate
- `solo_return_button` — Single-click block coordinate (optional)
- `mp_lobby` — Single-click location
- `mp_join_button` — Single-click block coordinate
- `mp_leader_create_button` — Single-click block coordinate (optional)
- `mp_leader_start_button` — Single-click block coordinate
- `mp_leader_cancel_button` — Single-click block coordinate

**Setup Session State:**
- Stored in: `SetupSessionManager` (Map<UUID, SetupSession>)
- Fields: `courseName`, `armedAction` (enum), `pendingPoint1` (BlockCoord, for two-click regions)
- Persistence: In-memory only, cleared on plugin disable or `/bocrace cancel`

**Region Capture Logic:**
- **Spawns/Lobby:** Uses center of clicked block above: `x = blockX + 0.5`, `y = blockY + 1.0`, `z = blockZ + 0.5`. Captures player yaw, forces pitch=0.
- **Start/Finish Regions:**
  - BOAT: Two-click flow, ground volume: `minY = min(cornerA.y, cornerB.y)`, `maxY = minY + 1`, X/Z from corner bounds
  - AIR: Two-click flow, full 3D cuboid: `minY = min(cornerA.y, cornerB.y)`, `maxY = max(cornerA.y, cornerB.y)`, X/Z from corner bounds
- **Checkpoints:** Two-click flow, stored as `point1` and `point2` BlockCoords (not as VolumeRegion)

**Setup Feedback:**
- Single-click actions: ActionBar message with coords/yaw, particle effect (`HAPPY_VILLAGER`), sound (`ENTITY_EXPERIENCE_ORB_PICKUP`)
- Region corner A: ActionBar message, particles, sound
- Region complete: ActionBar message with min/max bounds and region type (ground-volume/3D-cuboid), particles (`FIREWORK`), sound (`ENTITY_PLAYER_LEVELUP`)

---

## 9. Validation Rules

**Validator Class:** `CourseValidator` (`com.bocrace.util.CourseValidator`)

**Validation Checks (Read-Only):**
1. Course lobby: Must be set, world must be loaded, all subsequent checks require this
2. Player spawns: Must have at least 1 spawn, all spawn worlds must be loaded
3. Start region: Must exist, `min` and `max` must be non-null, world must be loaded, world must match course lobby world
4. Finish region: Must exist, `min` and `max` must be non-null, world must be loaded, world must match course lobby world
5. Checkpoints (conditional): Only validated if `settings.rules.requireCheckpoints == true`. If required: Must have at least 1 checkpoint, checkpoint indices must be sequential starting at 1 (1, 2, 3, ...)

**Validation Result:**
- Returns: `ValidationResult` object with `boolean ok` and `List<String> issues`
- Used by: `/bocrace validate` command (displays all issues), `/bocrace status` command (displays "Course Ready: YES/NO" and first issue if any)

**No Enforcement:**
- Validation does not block course creation or setup
- Validation does not change course state
- Courses can be created and used even if validation fails

---

## 10. Permission System

**Permission Nodes:**
- `bocrace.admin` — Full access to all admin commands (default: op)
- `bocrace.builder` — Access to course creation and setup commands (default: op)

**Permission Checks:**
- All `/bocrace` commands require either `bocrace.admin` or `bocrace.builder`
- OPs bypass permission checks (checked via `sender.isOp()`)
- Button interactions (player gameplay) do not require permissions

**Command Access:**
- `create`, `setup`, `status`, `validate`, `cancel` — Require admin or builder permission (or OP)
- Button clicks — No permission checks (any player can interact)

---

*This report documents the current implementation as of the codebase state.*
