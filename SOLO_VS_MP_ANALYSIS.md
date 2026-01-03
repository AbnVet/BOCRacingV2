# SOLO vs MULTIPLAYER Setup Analysis

## Current Implementation

### Mode Derivation
- **SOLO:** Course has exactly **1 player spawn**
- **MULTIPLAYER:** Course has **2+ player spawns**
- Mode is **derived automatically** from spawn count (not explicitly set)

### Setup Requirements

#### Common Requirements (Both SOLO & MP):
- `course_lobby` - Single lobby spawn location
- `player_spawn` - At least 1 spawn (SOLO needs exactly 1, MP needs 2+)
- `start` region - Two-click ground volume (BOAT: height=2, AIR: full 3D)
- `finish` region - Two-click ground volume (BOAT: height=2, AIR: full 3D)
- `checkpoints` - Optional (required only if `settings.rules.requireCheckpoints=true`)

#### SOLO-Specific Requirements:
- `solo_join_button` - **MANDATORY** - Button players click to start solo race
- `solo_return_button` - **OPTIONAL** - Button to return to lobby and clear lock

**SOLO Flow:**
1. Player clicks `solo_join_button`
2. Check: spawnCount == 1 AND soloJoinButton exists
3. Check: No active solo lock (or expired)
4. Acquire solo lock (cooldown: `settings.soloCooldownSeconds`, default 120s)
5. Teleport to spawn[0]
6. Create ActiveRun
7. Start countdown → GO → Start timer (CROSS_LINE or DROP_START)
8. Race detection → Finish → Clear lock

#### MULTIPLAYER-Specific Requirements:
- `mp_lobby` - **MANDATORY** - Where players return if race cancelled
- `mp_join_button` - **MANDATORY** - Button players click to join lobby
- `mp_leader_start_button` - **MANDATORY** - Button leader clicks to start race
- `mp_leader_cancel_button` - **MANDATORY** - Button leader clicks to cancel
- `mp_leader_create_button` - **OPTIONAL** - Button to create/announce lobby

**MP Flow:**
1. Players click `mp_join_button` → Join lobby, get assigned spawn index
2. Leader clicks `mp_leader_start_button` → Countdown starts for all
3. After GO → All racers start simultaneously
4. Race detection → Finish → Cleanup when all finished
5. Leader clicks `mp_leader_cancel_button` → All teleported to `mp_lobby`

## Key Differences Summary

| Aspect | SOLO | MULTIPLAYER |
|--------|------|-------------|
| **Spawn Count** | Exactly 1 | 2 or more |
| **Join Method** | `solo_join_button` | `mp_join_button` |
| **Start Method** | Automatic after countdown | Leader presses `mp_leader_start_button` |
| **Lobby System** | None (direct teleport) | Lobby with spawn assignment |
| **Lock/Cooldown** | SoloLock (prevents concurrent runs) | No lock (multiple racers) |
| **Return Button** | `solo_return_button` (optional) | `mp_leader_cancel_button` (mandatory) |
| **Return Location** | `course_lobby` | `mp_lobby` (separate field) |

## Proposed Command Structure

### Option 1: Explicit Mode at Creation (Recommended)
```
/bocrace create <boatrace|airrace> <solo|multiplayer> <name>
```

**Benefits:**
- Clear intent from the start
- Can validate mode-specific requirements immediately
- Status can show "Mode: SOLO" or "Mode: MULTIPLAYER" explicitly
- Setup guidance can be mode-specific

**Example:**
```
/bocrace create boatrace solo TestSolo
/bocrace create airrace multiplayer TestMP
```

### Option 2: Keep Current + Add Mode Hint
```
/bocrace create <boatrace|airrace> <name>
/bocrace setup <name> <action>
```

Add mode detection and hints:
- Status shows: "Mode: SOLO (1 spawn)" or "Mode: MULTIPLAYER (3 spawns)"
- Setup actions filtered by mode (solo_* only for SOLO, mp_* only for MP)
- Validation warns if spawn count doesn't match intended mode

### Option 3: Separate Commands (Most Explicit)
```
/bocrace create-solo <boatrace|airrace> <name>
/bocrace create-multiplayer <boatrace|airrace> <name>
```

**Benefits:**
- Very clear separation
- Can have mode-specific validation from creation
- Setup actions can be pre-filtered

## Recommendation

**Option 1** is best because:
1. Keeps command structure simple (one `create` command)
2. Makes mode explicit without being verbose
3. Allows mode-specific validation and guidance
4. Status can show mode clearly

## Implementation Notes

If we implement Option 1:
- Add `mode` field to Course model (SOLO/MULTIPLAYER enum)
- Validate on create: if `solo` mode, require spawnCount==1; if `multiplayer`, require spawnCount>=2
- Update status output to show mode explicitly
- Filter setup actions by mode (don't show `mp_*` actions for SOLO courses)
- Keep spawn-count derivation as fallback/validation
