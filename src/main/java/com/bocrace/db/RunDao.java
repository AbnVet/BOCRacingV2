package com.bocrace.db;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.util.DebugLog;
import org.bukkit.Bukkit;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Data Access Object for race runs
 * All operations use DbDispatcher for ordered execution
 */
public class RunDao {
    
    private final BOCRacingV2 plugin;
    private final DataSource dataSource;
    private final DbDispatcher dispatcher;
    private final Map<String, CountDownLatch> pendingCreates; // Track pending createRun operations
    
    public RunDao(BOCRacingV2 plugin, DataSource dataSource, DbDispatcher dispatcher) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.dispatcher = dispatcher;
        this.pendingCreates = new ConcurrentHashMap<>();
    }
    
    /**
     * Create a new run record (async-safe, ensures ordering)
     */
    public void createRun(String runId, String courseKey, String courseType, String courseFile,
                         UUID playerUuid, Course.StartMode startMode, boolean requireCheckpoints,
                         Course.DropSettings.DropShape dropShape) {
        CountDownLatch latch = new CountDownLatch(1);
        pendingCreates.put(runId, latch);
        
        dispatcher.submit(() -> {
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
                
                // Signal that create completed
                latch.countDown();
                pendingCreates.remove(runId);
            } catch (SQLException e) {
                latch.countDown();
                pendingCreates.remove(runId);
                plugin.getDebugLog().error("RunDao", "Failed to create run", e);
            }
        });
    }
    
    /**
     * Ensure run exists before proceeding (waits for createRun if pending, checks existence)
     */
    private boolean ensureRunExists(String runId, String operation) {
        // Check if createRun is still pending
        CountDownLatch latch = pendingCreates.get(runId);
        if (latch != null) {
            try {
                // Wait up to 1 second for createRun to complete
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    Map<String, Object> kv = new HashMap<>();
                    kv.put("runId", runId);
                    kv.put("operation", operation);
                    kv.put("error", "createRun timeout");
                    plugin.getDebugLog().error("RunDao", "DB_ORDER: createRun did not complete in time", null, kv);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Check if run exists in database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM runs WHERE run_id = ?")) {
            stmt.setString(1, runId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getDebugLog().error("RunDao", "Failed to check run existence", e);
            return false;
        }
    }
    
    /**
     * Mark run as started (set start_millis) (async-safe, waits for createRun)
     */
    public void markStarted(String runId, long startMillis, String courseKey, UUID playerUuid) {
        dispatcher.submit(() -> {
            if (!ensureRunExists(runId, "markStarted")) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                plugin.getDebugLog().error("RunDao", "DB_ORDER: markStarted called before createRun completed", null, kv);
                // Retry once after short delay
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!ensureRunExists(runId, "markStarted-retry")) {
                    plugin.getDebugLog().error("RunDao", "DB_ORDER: markStarted retry failed, run does not exist", null, kv);
                    return;
                }
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE runs SET start_millis = ?, status = ? WHERE run_id = ?")) {
                
                stmt.setLong(1, startMillis);
                stmt.setString(2, "STARTED");
                stmt.setString(3, runId);
                
                stmt.executeUpdate();
                
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                kv.put("startMillis", startMillis);
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run started", kv);
            } catch (SQLException e) {
                plugin.getDebugLog().error("RunDao", "Failed to mark run started", e);
            }
        });
    }
    
    /**
     * Record a checkpoint split time (async-safe, waits for createRun)
     */
    public void recordCheckpoint(String runId, int checkpointIndex, long splitMillis, String courseKey, UUID playerUuid) {
        dispatcher.submit(() -> {
            if (!ensureRunExists(runId, "recordCheckpoint")) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("checkpointIndex", checkpointIndex);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                plugin.getDebugLog().error("RunDao", "DB_ORDER: recordCheckpoint called before createRun completed", null, kv);
                return;
            }
            
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
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                kv.put("checkpointIndex", checkpointIndex);
                kv.put("splitMillis", splitMillis);
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Checkpoint recorded", kv);
            } catch (SQLException e) {
                plugin.getDebugLog().error("RunDao", "Failed to record checkpoint", e);
            }
        });
    }
    
    /**
     * Finish a run (set finish_millis, duration_millis, status) (async-safe, waits for createRun)
     */
    public void finishRun(String runId, long finishMillis, long durationMillis, String courseKey, UUID playerUuid) {
        dispatcher.submit(() -> {
            if (!ensureRunExists(runId, "finishRun")) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                plugin.getDebugLog().error("RunDao", "DB_ORDER: finishRun called before createRun completed", null, kv);
                return;
            }
            
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
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                kv.put("finishMillis", finishMillis);
                kv.put("durationMillis", durationMillis);
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run finished", kv);
            } catch (SQLException e) {
                plugin.getDebugLog().error("RunDao", "Failed to finish run", e);
            }
        });
    }
    
    /**
     * Abort a run (set status=ABORTED) (async-safe, waits for createRun)
     */
    public void abortRun(String runId, String reason, String courseKey, UUID playerUuid) {
        dispatcher.submit(() -> {
            if (!ensureRunExists(runId, "abortRun")) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                plugin.getDebugLog().error("RunDao", "DB_ORDER: abortRun called before createRun completed", null, kv);
                return;
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE runs SET status = ?, dq_reason = ? WHERE run_id = ?")) {
                
                stmt.setString(1, "ABORTED");
                stmt.setString(2, reason);
                stmt.setString(3, runId);
                
                stmt.executeUpdate();
                
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                kv.put("reason", reason);
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run aborted", kv);
            } catch (SQLException e) {
                plugin.getDebugLog().error("RunDao", "Failed to abort run", e);
            }
        });
    }
    
    /**
     * Disqualify a run (set status=DQ, dq_reason) (async-safe, waits for createRun)
     */
    public void dqRun(String runId, String reason, String courseKey, UUID playerUuid) {
        dispatcher.submit(() -> {
            if (!ensureRunExists(runId, "dqRun")) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                plugin.getDebugLog().error("RunDao", "DB_ORDER: dqRun called before createRun completed", null, kv);
                return;
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE runs SET status = ?, dq_reason = ? WHERE run_id = ?")) {
                
                stmt.setString(1, "DQ");
                stmt.setString(2, reason);
                stmt.setString(3, runId);
                
                stmt.executeUpdate();
                
                Map<String, Object> kv = new HashMap<>();
                kv.put("runId", runId);
                kv.put("courseKey", courseKey);
                kv.put("playerUuid", playerUuid.toString());
                kv.put("reason", reason);
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "RunDao", "Run DQed", kv);
            } catch (SQLException e) {
                plugin.getDebugLog().error("RunDao", "Failed to DQ run", e);
            }
        });
    }
}
