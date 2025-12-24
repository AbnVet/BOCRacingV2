package com.bocrace.db;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.util.DebugLog;
import org.bukkit.scheduler.BukkitRunnable;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Access Object for race runs
 */
public class RunDao {
    
    private final BOCRacingV2 plugin;
    private final DataSource dataSource;
    
    public RunDao(BOCRacingV2 plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }
    
    /**
     * Create a new run record (async-safe)
     */
    public void createRun(String runId, String courseKey, String courseType, String courseFile,
                         UUID playerUuid, Course.StartMode startMode, boolean requireCheckpoints,
                         Course.DropSettings.DropShape dropShape) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO runs (run_id, course_key, course_type, course_file, player_uuid, " +
                         "start_mode, require_checkpoints, drop_shape, status, created_millis) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    
                    long now = System.currentTimeMillis();
                    stmt.setString(1, runId);
                    stmt.setString(2, courseKey);
                    stmt.setString(3, courseType);
                    stmt.setString(4, courseFile);
                    stmt.setString(5, playerUuid.toString());
                    stmt.setString(6, startMode.name());
                    stmt.setInt(7, requireCheckpoints ? 1 : 0);
                    stmt.setString(8, dropShape != null ? dropShape.name() : null);
                    stmt.setString(9, "ACTIVE");
                    stmt.setLong(10, now);
                    
                    stmt.executeUpdate();
                    
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("courseKey", courseKey);
                    kv.put("playerUuid", playerUuid.toString());
                    plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run created", kv);
                } catch (SQLException e) {
                    plugin.getDebugLog().error("RunDao", "Failed to create run", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Mark run as started (set start_millis) (async-safe)
     */
    public void markStarted(String runId, long startMillis) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE runs SET start_millis = ?, status = ? WHERE run_id = ?")) {
                    
                    stmt.setLong(1, startMillis);
                    stmt.setString(2, "STARTED");
                    stmt.setString(3, runId);
                    
                    stmt.executeUpdate();
                    
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("startMillis", startMillis);
                    plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run started", kv);
                } catch (SQLException e) {
                    plugin.getDebugLog().error("RunDao", "Failed to mark run started", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Record a checkpoint split time (async-safe)
     */
    public void recordCheckpoint(String runId, int checkpointIndex, long splitMillis) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO run_checkpoints (run_id, checkpoint_index, split_millis) " +
                         "VALUES (?, ?, ?)")) {
                    
                    stmt.setString(1, runId);
                    stmt.setInt(2, checkpointIndex);
                    stmt.setLong(3, splitMillis);
                    
                    stmt.executeUpdate();
                    
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("checkpointIndex", checkpointIndex);
                    kv.put("splitMillis", splitMillis);
                    plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Checkpoint recorded", kv);
                } catch (SQLException e) {
                    plugin.getDebugLog().error("RunDao", "Failed to record checkpoint", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Finish a run (set finish_millis, duration_millis, status) (async-safe)
     */
    public void finishRun(String runId, long finishMillis, long durationMillis) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE runs SET finish_millis = ?, duration_millis = ?, status = ? WHERE run_id = ?")) {
                    
                    stmt.setLong(1, finishMillis);
                    stmt.setLong(2, durationMillis);
                    stmt.setString(3, "FINISHED");
                    stmt.setString(4, runId);
                    
                    stmt.executeUpdate();
                    
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("finishMillis", finishMillis);
                    kv.put("durationMillis", durationMillis);
                    plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run finished", kv);
                } catch (SQLException e) {
                    plugin.getDebugLog().error("RunDao", "Failed to finish run", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Abort a run (set status=ABORTED) (async-safe)
     */
    public void abortRun(String runId, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE runs SET status = ?, dq_reason = ? WHERE run_id = ?")) {
                    
                    stmt.setString(1, "ABORTED");
                    stmt.setString(2, reason);
                    stmt.setString(3, runId);
                    
                    stmt.executeUpdate();
                    
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("reason", reason);
                    plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run aborted", kv);
                } catch (SQLException e) {
                    plugin.getDebugLog().error("RunDao", "Failed to abort run", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Disqualify a run (set status=DQ, dq_reason) (async-safe)
     */
    public void dqRun(String runId, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE runs SET status = ?, dq_reason = ? WHERE run_id = ?")) {
                    
                    stmt.setString(1, "DQ");
                    stmt.setString(2, reason);
                    stmt.setString(3, runId);
                    
                    stmt.executeUpdate();
                    
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("reason", reason);
                    plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run DQed", kv);
                } catch (SQLException e) {
                    plugin.getDebugLog().error("RunDao", "Failed to DQ run", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
