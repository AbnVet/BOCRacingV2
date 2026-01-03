# Testing Issues & Analysis

## Critical Issues Found

### 1. Database Migration Not Running
**Error:** `[WARN] No migrations found. Are your locations set up correctly?`

**Root Cause:** Flyway migration file exists but isn't being packaged into the JAR correctly, or resource path is wrong.

**Fix Needed:** Ensure `src/main/resources/db/migration/V1__Initial_schema.sql` is included in the JAR. The maven-shade-plugin should handle this, but we may need to verify the resource is being copied.

### 2. Missing getQueryDao() Getter
**Status:** ✅ FIXED - Added back to BOCRacingV2.java

### 3. Button Click Not Working
**Symptom:** "When I created a race and went to select the begin race that it just marked it"

**Possible Causes:**
- Course readiness check failing (missing spawns, missing button setup)
- Button detection not matching coordinates
- World name mismatch

**Debug Steps:**
1. Check `/bocrace status <course>` - what does it show?
2. Verify button was saved correctly (check YAML file)
3. Check console for "Course not ready yet" messages
4. Verify spawn count matches mode (1 for SOLO, 2+ for MP)

## SOLO vs MULTIPLAYER Setup Analysis

### Current System (Mode Derived from Spawn Count)
- **SOLO:** Exactly 1 spawn → Uses `solo_join_button`, `solo_return_button`
- **MULTIPLAYER:** 2+ spawns → Uses `mp_join_button`, `mp_leader_start_button`, `mp_leader_cancel_button`, `mp_lobby`

### Setup Requirements Comparison

#### SOLO Course Requirements:
- ✅ `course_lobby` (single location)
- ✅ `player_spawn` (exactly 1)
- ✅ `start` region (2-block ground volume for BOAT, 3D cuboid for AIR)
- ✅ `finish` region (2-block ground volume for BOAT, 3D cuboid for AIR)
- ✅ `solo_join_button` (mandatory)
- ⚠️ `solo_return_button` (optional)
- ⚠️ `checkpoints` (optional unless `requireCheckpoints=true`)

#### MULTIPLAYER Course Requirements:
- ✅ `course_lobby` (single location)
- ✅ `player_spawn` (2 or more)
- ✅ `start` region (2-block ground volume for BOAT, 3D cuboid for AIR)
- ✅ `finish` region (2-block ground volume for BOAT, 3D cuboid for AIR)
- ✅ `mp_lobby` (where players return if cancelled)
- ✅ `mp_join_button` (mandatory)
- ✅ `mp_leader_start_button` (mandatory)
- ✅ `mp_leader_cancel_button` (mandatory)
- ⚠️ `mp_leader_create_button` (optional)
- ⚠️ `checkpoints` (optional unless `requireCheckpoints=true`)

### Key Differences:
1. **Spawn Count:** SOLO = 1, MP = 2+
2. **Buttons:** Different button sets (solo_* vs mp_*)
3. **Lobby:** MP has separate `mp_lobby` (can reuse `course_lobby` but stored separately)
4. **Flow:** SOLO = immediate countdown, MP = lobby system with leader

## Proposed Command Structure Improvement

### Current Structure:
```
/bocrace create <boatrace|airrace> <name>
/bocrace setup <name> <action>
```

### Proposed Structure (Explicit Mode):
```
/bocrace create <boatrace|airrace> <solo|multiplayer> <name>
/bocrace setup <name> <action>
```

**Benefits:**
- Clearer intent at creation time
- Can validate mode-specific requirements earlier
- Better UX for admins
- Status output can show "Mode: SOLO" or "Mode: MULTIPLAYER" explicitly

**Alternative (Keep Current, Add Mode Hint):**
- Keep spawn-count derivation
- Add `/bocrace setup <name> solo` or `/bocrace setup <name> multiplayer` to show mode-specific setup checklist
- Status command shows derived mode clearly

## Recommended Next Steps

1. **Fix Database Migration:**
   - Verify migration file is in JAR
   - Check Flyway resource loading
   - Manually create tables if needed for testing

2. **Debug Button Issue:**
   - Run `/bocrace status <course>` and report output
   - Check if spawn count matches expected mode
   - Verify button coordinates in YAML match clicked block

3. **Improve Command UX:**
   - Add mode hint to create command
   - Improve status output to show mode explicitly
   - Add mode-specific setup guidance

4. **Test Checklist:**
   - Verify database tables exist after server start
   - Test SOLO course with exactly 1 spawn
   - Test MP course with 2+ spawns
   - Verify button clicks trigger correct handlers
