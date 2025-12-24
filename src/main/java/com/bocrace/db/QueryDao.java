package com.bocrace.db;

import com.bocrace.BOCRacingV2;
import com.bocrace.util.DebugLog;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Data Access Object for read-only queries
 * All operations use DbDispatcher for ordered execution
 */
public class QueryDao {
    
    public static class TopTime {
        private final String playerName;
        private final UUID playerUuid;
        private final long durationMillis;
        private final long finishMillis;
        
        public TopTime(String playerName, UUID playerUuid, long durationMillis, long finishMillis) {
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.durationMillis = durationMillis;
            this.finishMillis = finishMillis;
        }
        
        public String getPlayerName() { return playerName; }
        public UUID getPlayerUuid() { return playerUuid; }
        public long getDurationMillis() { return durationMillis; }
        public long getFinishMillis() { return finishMillis; }
    }
    
    public static class PlayerRun {
        private final String runId;
        private final String courseKey;
        private final long durationMillis;
        private final long finishMillis;
        private final String status;
        
        public PlayerRun(String runId, String courseKey, long durationMillis, long finishMillis, String status) {
            this.runId = runId;
            this.courseKey = courseKey;
            this.durationMillis = durationMillis;
            this.finishMillis = finishMillis;
            this.status = status;
        }
        
        public String getRunId() { return runId; }
        public String getCourseKey() { return courseKey; }
        public long getDurationMillis() { return durationMillis; }
        public long getFinishMillis() { return finishMillis; }
        public String getStatus() { return status; }
    }
    
    private final BOCRacingV2 plugin;
    private final DataSource dataSource;
    private final DbDispatcher dispatcher;
    
    public QueryDao(BOCRacingV2 plugin, DataSource dataSource, DbDispatcher dispatcher) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.dispatcher = dispatcher;
    }
    
    /**
     * Get top times for a course (async, returns Future)
     */
    public Future<List<TopTime>> getTopTimes(String courseKey, int limit) {
        CompletableFuture<List<TopTime>> future = new CompletableFuture<>();
        
        dispatcher.submit(() -> {
            List<TopTime> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT p.last_name, r.player_uuid, r.duration_millis, r.finish_millis " +
                     "FROM runs r " +
                     "JOIN players p ON r.player_uuid = p.uuid " +
                     "WHERE r.course_key = ? AND r.status = 'FINISHED' " +
                     "ORDER BY r.duration_millis ASC " +
                     "LIMIT ?")) {
                
                stmt.setString(1, courseKey);
                stmt.setInt(2, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String playerName = rs.getString("last_name");
                        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                        long durationMillis = rs.getLong("duration_millis");
                        long finishMillis = rs.getLong("finish_millis");
                        results.add(new TopTime(playerName, playerUuid, durationMillis, finishMillis));
                    }
                }
                
                Map<String, Object> kv = new HashMap<>();
                kv.put("courseKey", courseKey);
                kv.put("limit", limit);
                kv.put("count", results.size());
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "QueryDao", "Top times queried", kv);
                
                future.complete(results);
            } catch (Exception e) {
                plugin.getDebugLog().error("QueryDao", "Failed to get top times", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Get player's best time for a course (async, returns Future)
     */
    public Future<TopTime> getPlayerBest(String courseKey, UUID playerUuid) {
        CompletableFuture<TopTime> future = new CompletableFuture<>();
        
        dispatcher.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT p.last_name, r.player_uuid, r.duration_millis, r.finish_millis " +
                     "FROM runs r " +
                     "JOIN players p ON r.player_uuid = p.uuid " +
                     "WHERE r.course_key = ? AND r.player_uuid = ? AND r.status = 'FINISHED' " +
                     "ORDER BY r.duration_millis ASC " +
                     "LIMIT 1")) {
                
                stmt.setString(1, courseKey);
                stmt.setString(2, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String playerName = rs.getString("last_name");
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        long durationMillis = rs.getLong("duration_millis");
                        long finishMillis = rs.getLong("finish_millis");
                        
                        Map<String, Object> kv = new HashMap<>();
                        kv.put("courseKey", courseKey);
                        kv.put("playerUuid", playerUuid.toString());
                        kv.put("durationMillis", durationMillis);
                        plugin.getDebugLog().info(DebugLog.Tag.DATA, "QueryDao", "Player best queried", kv);
                        
                        future.complete(new TopTime(playerName, uuid, durationMillis, finishMillis));
                    } else {
                        future.complete(null);
                    }
                }
            } catch (Exception e) {
                plugin.getDebugLog().error("QueryDao", "Failed to get player best", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Get player's recent runs (async, returns Future)
     */
    public Future<List<PlayerRun>> getPlayerRecentRuns(UUID playerUuid, int limit) {
        CompletableFuture<List<PlayerRun>> future = new CompletableFuture<>();
        
        dispatcher.submit(() -> {
            List<PlayerRun> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT run_id, course_key, duration_millis, finish_millis, status " +
                     "FROM runs " +
                     "WHERE player_uuid = ? " +
                     "ORDER BY created_millis DESC " +
                     "LIMIT ?")) {
                
                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String runId = rs.getString("run_id");
                        String courseKey = rs.getString("course_key");
                        long durationMillis = rs.getLong("duration_millis");
                        long finishMillis = rs.getLong("finish_millis");
                        String status = rs.getString("status");
                        results.add(new PlayerRun(runId, courseKey, durationMillis, finishMillis, status));
                    }
                }
                
                Map<String, Object> kv = new HashMap<>();
                kv.put("playerUuid", playerUuid.toString());
                kv.put("limit", limit);
                kv.put("count", results.size());
                plugin.getDebugLog().info(DebugLog.Tag.DATA, "QueryDao", "Player recent runs queried", kv);
                
                future.complete(results);
            } catch (Exception e) {
                plugin.getDebugLog().error("QueryDao", "Failed to get player recent runs", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
}
