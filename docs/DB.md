# BOCRacingV2 Database Documentation

## Overview

BOCRacingV2 uses a database to store race records and player data. The database system supports both SQLite (embedded) and MySQL/MariaDB (for multi-server setups).

## Configuration

Database settings are configured in `plugins/BOCRacingV2/config.yml`:

```yaml
database:
  type: SQLITE  # or MYSQL
  sqlite:
    file: bocracing.db
  mysql:
    host: localhost
    port: 3306
    database: bocracing
    username: root
    password: password
  pool:
    maxConnections: 10
```

**Type Options:**
- `SQLITE`: Embedded SQLite database (recommended for single-server setups)
  - Database file stored in plugin data folder
  - No external database server required
- `MYSQL`: MySQL/MariaDB database (required for multi-server setups)
  - Requires external database server
  - Allows shared data across multiple servers

**Connection Pool:**
- `maxConnections`: Maximum number of concurrent database connections
  - Default: 10 for SQLite (capped at 5), 20 for MySQL
  - Adjust based on server load and database capacity

## Schema Version 1

### Tables

#### `players`
Stores player UUID and last seen information.

| Column | Type | Description |
|--------|------|-------------|
| `uuid` | TEXT | Primary key, player UUID |
| `last_name` | TEXT | Player's last known name |
| `last_seen` | BIGINT | Unix timestamp (milliseconds) of last update |

#### `runs`
Stores race run records with timing and status information.

| Column | Type | Description |
|--------|------|-------------|
| `run_id` | TEXT | Primary key, unique run identifier (UUID) |
| `course_key` | TEXT | Course name (indexed) |
| `course_type` | TEXT | Course type (BOAT/AIR) |
| `course_file` | TEXT | Course YAML filename (e.g., "my_course.yml") |
| `player_uuid` | TEXT | Foreign key to `players.uuid` (indexed) |
| `start_mode` | TEXT | Start mode (CROSS_LINE/DROP_START) |
| `require_checkpoints` | INT | 1 if checkpoints required, 0 otherwise |
| `drop_shape` | TEXT | Drop shape for DROP_START (SINGLE/SQUARE/CIRCLE or NULL) |
| `status` | TEXT | Run status: ACTIVE, STARTED, FINISHED, ABORTED, DQ (indexed) |
| `dq_reason` | TEXT | Disqualification reason (if status=DQ) |
| `start_millis` | BIGINT | Unix timestamp (ms) when timer started (NULL until started) |
| `finish_millis` | BIGINT | Unix timestamp (ms) when race finished (NULL until finished) |
| `duration_millis` | BIGINT | Race duration in milliseconds (NULL until finished) |
| `created_millis` | BIGINT | Unix timestamp (ms) when run record was created (indexed) |

**Indexes:**
- `idx_runs_course_key` on `course_key`
- `idx_runs_player_uuid` on `player_uuid`
- `idx_runs_status` on `status`
- `idx_runs_created_millis` on `created_millis`

#### `run_checkpoints`
Stores checkpoint split times for each run.

| Column | Type | Description |
|--------|------|-------------|
| `run_id` | TEXT | Foreign key to `runs.run_id` (CASCADE DELETE) |
| `checkpoint_index` | INT | Checkpoint index (1-based) |
| `split_millis` | BIGINT | Milliseconds from start to checkpoint |
| PRIMARY KEY | (`run_id`, `checkpoint_index`) | Composite primary key |

**Foreign Keys:**
- `run_id` references `runs.run_id` ON DELETE CASCADE

## Migrations

Database schema migrations are handled by Flyway and stored in:
- `src/main/resources/db/migration/`

**Current Migration:**
- `V1__Initial_schema.sql`: Creates initial schema with players, runs, and run_checkpoints tables

Flyway automatically applies pending migrations on plugin startup. The schema version is tracked by Flyway's internal metadata tables.

## Data Access Objects (DAOs)

### RunDao

**Methods (all async-safe, non-blocking):**

- `createRun(runId, courseKey, courseType, courseFile, playerUuid, startMode, requireCheckpoints, dropShape)`
  - Creates a new run record with status=ACTIVE
  - Called when ActiveRun is created

- `markStarted(runId, startMillis)`
  - Updates run status to STARTED and sets start_millis
  - Called when timer starts (CROSS_LINE or DROP_START)

- `recordCheckpoint(runId, checkpointIndex, splitMillis)`
  - Records a checkpoint split time
  - Called when player passes a checkpoint

- `finishRun(runId, finishMillis, durationMillis)`
  - Updates run status to FINISHED and sets finish_millis and duration_millis
  - Called when player finishes race

- `abortRun(runId, reason)`
  - Updates run status to ABORTED and sets dq_reason
  - Called when race is cancelled or player leaves

- `dqRun(runId, reason)`
  - Updates run status to DQ and sets dq_reason
  - Called when player is disqualified (future use)

### PlayerDao

**Methods (all async-safe, non-blocking):**

- `upsertPlayer(uuid, lastName)`
  - Inserts or updates player record (last_name, last_seen)
  - Called when a run is created (ensures player record exists)

## Database Call Sites

All database operations are executed asynchronously to avoid blocking the main server thread.

**Run Creation:**
- `CourseButtonListener.handleSoloJoin()` - SOLO run
- `CourseButtonListener.handleMpLeaderStart()` - MP run

**Run Started:**
- `CourseButtonListener` (countdown GO for DROP_START)
- `RaceDetectionTask.run()` (CROSS_LINE start trigger)

**Checkpoint Recorded:**
- `RaceDetectionTask.checkCheckpoints()` (when checkpoint passed)

**Run Finished:**
- `RaceDetectionTask.finishRace()` (when finish line crossed)

**Run Aborted:**
- `CourseButtonListener.handleMpLeaderCancel()` (leader cancels)
- `PlayerLifecycleListener.handlePlayerLeave()` (player quits/kicks)

## Status Values

**Run Status Flow:**
1. `ACTIVE` - Run created, waiting for timer to start
2. `STARTED` - Timer has started (start_millis set)
3. `FINISHED` - Race completed successfully (finish_millis and duration_millis set)
4. `ABORTED` - Race cancelled or player left (dq_reason set)
5. `DQ` - Race disqualified (dq_reason set) - reserved for future use

## Performance Considerations

- All database operations are async-safe (run on async thread pool)
- Connection pooling via HikariCP prevents connection exhaustion
- Indexes on frequently queried columns (course_key, player_uuid, status, created_millis)
- Foreign key constraints ensure referential integrity
- CASCADE DELETE on run_checkpoints ensures cleanup when runs are deleted

## Debug Logging

All database operations are logged to `debug.log` (if enabled) with `Tag.DATA`:
- Run created/started/finished/aborted
- Checkpoint recorded
- Player upserted

Database errors are logged with `Tag.ERROR` and always appear in console.
