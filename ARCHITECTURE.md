# ARCHITECTURE - BOCRacingV2 Design

## Course-Driven Model

### Course Mode Derivation
- **SOLO**: Course has exactly 1 spawn point
- **MULTIPLAYER**: Course has 2+ spawn points
- Mode is derived at runtime from `course_spawns` count, not stored as a field.

### Course Entity
- Single unified `Course` class for both boat and air races
- Race type determined by course configuration (not separate classes)

## Admin Course Builder Workflow (Evolving)

This workflow represents the current design intent and may be refined
as usability issues are discovered during implementation.

### Setup Phase
1. Create course record in `courses` table
2. Define course boundaries/checkpoints
3. Add spawn points to `course_spawns` table
4. Configure race type (boat/air) and settings

### Status Tracking
- **DRAFT**: Course being configured, not playable
- **VALIDATING**: Admin testing the course
- **PUBLISHED**: Course available for players
- **ARCHIVED**: Course disabled but preserved

### Validation Checklist
- [ ] All spawn points are valid locations
- [ ] Checkpoints are properly sequenced
- [ ] Finish line is correctly configured
- [ ] Course boundaries prevent shortcuts
- [ ] Test run completes successfully

### Publish
- Set course status to `PUBLISHED`
- Course becomes available in race selection
- Players can start races on this course

## Database Schema

### Core Tables

#### `courses`
- `id` (PRIMARY KEY)
- `name` (UNIQUE)
- `type` (BOAT/AIR)
- `status` (DRAFT/VALIDATING/PUBLISHED/ARCHIVED)
- `world` (UUID)
- `created_at`, `updated_at`

#### `course_spawns`
- `id` (PRIMARY KEY)
- `course_id` (FOREIGN KEY → courses.id)
- `spawn_index` (INT, order of spawn)
- `x`, `y`, `z` (location)
- `yaw`, `pitch` (rotation)

#### `runs`
- `id` (PRIMARY KEY)
- `course_id` (FOREIGN KEY → courses.id)
- `started_at`, `finished_at`
- `status` (IN_PROGRESS/COMPLETED/DISQUALIFIED)
- **Note**: Mode is derived dynamically from spawn count or participant count.
  No mode field is stored.

#### `run_players`
- `id` (PRIMARY KEY)
- `run_id` (FOREIGN KEY → runs.id)
- `player_uuid` (UUID)
- `spawn_index` (which spawn they used)
- `finished_at` (NULL if DQ or in progress)
- `final_time_ms` (NULL if not finished)
- `position` (1, 2, 3... for multiplayer)

#### `run_splits` (Optional - for checkpoint timing)
- `id` (PRIMARY KEY)
- `run_id` (FOREIGN KEY → runs.id)
- `player_uuid` (UUID)
- `checkpoint_index` (INT)
- `split_time_ms` (time from start to this checkpoint)
- `recorded_at` (timestamp)

## Placeholder Contract

### Placeholder Names and Required Queries

#### `%bocrace_version%`
- **Query**: None (static plugin version)
- **Returns**: Plugin version string

#### `%bocrace_course_name%`
- **Query**: Current player's active course
- **Returns**: Course name or empty

#### `%bocrace_course_type%`
- **Query**: Current player's active course
- **Returns**: "BOAT" or "AIR"

#### `%bocrace_course_mode%`
- **Query**: Current player's active course + spawn count
- **Returns**: "SOLO" or "MULTIPLAYER"

#### `%bocrace_run_time%`
- **Query**: Current player's active run start time
- **Returns**: Elapsed time in MM:SS.mmm format

#### `%bocrace_run_position%`
- **Query**: Current player's position in multiplayer run
- **Returns**: "1st", "2nd", "3rd", etc. or empty for solo

#### `%bocrace_pb_time%`
- **Query**: Player's personal best for current course
- **Returns**: Time in MM:SS.mmm or "N/A"

#### `%bocrace_pb_rank%`
- **Query**: Player's rank on current course leaderboard
- **Returns**: "1st", "2nd", "3rd", etc. or "N/A"

#### `%bocrace_course_record%`
- **Query**: Best time ever on current course
- **Returns**: Time in MM:SS.mmm or "N/A"

#### `%bocrace_course_record_holder%`
- **Query**: Player who holds record on current course
- **Returns**: Player name or "N/A"

### Query Patterns

All placeholders that require database access should:
- Cache results per-tick or per-second (not per-placeholder-call)
- Use prepared statements
- Handle NULL/empty cases gracefully
- Return "N/A" or empty string when no data available
