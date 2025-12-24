-- BOCRacingV2 Database Schema v1
-- Initial schema for players, runs, and run_checkpoints tables

-- Players table: tracks player UUIDs and last seen timestamps
CREATE TABLE IF NOT EXISTS players (
    uuid TEXT PRIMARY KEY,
    last_name TEXT NOT NULL,
    last_seen BIGINT NOT NULL
);

-- Runs table: stores race run records
CREATE TABLE IF NOT EXISTS runs (
    run_id TEXT PRIMARY KEY,
    course_key TEXT NOT NULL,
    course_type TEXT NOT NULL,
    course_file TEXT,
    player_uuid TEXT NOT NULL,
    start_mode TEXT NOT NULL,
    require_checkpoints INT NOT NULL,
    drop_shape TEXT,
    status TEXT NOT NULL,
    dq_reason TEXT,
    start_millis BIGINT,
    finish_millis BIGINT,
    duration_millis BIGINT,
    created_millis BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES players(uuid)
);

-- Run checkpoints table: stores checkpoint split times
CREATE TABLE IF NOT EXISTS run_checkpoints (
    run_id TEXT NOT NULL,
    checkpoint_index INT NOT NULL,
    split_millis BIGINT NOT NULL,
    PRIMARY KEY (run_id, checkpoint_index),
    FOREIGN KEY (run_id) REFERENCES runs(run_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_runs_course_key ON runs(course_key);
CREATE INDEX IF NOT EXISTS idx_runs_player_uuid ON runs(player_uuid);
CREATE INDEX IF NOT EXISTS idx_runs_status ON runs(status);
CREATE INDEX IF NOT EXISTS idx_runs_created_millis ON runs(created_millis);
