package com.bocrace.db;

import com.bocrace.BOCRacingV2;
import com.bocrace.util.DebugLog;
import org.bukkit.scheduler.BukkitRunnable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Access Object for player records
 */
public class PlayerDao {
    
    private final BOCRacingV2 plugin;
    private final DataSource dataSource;
    
    public PlayerDao(BOCRacingV2 plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }
    
    /**
     * Upsert player record (insert or update) (async-safe)
     */
    public void upsertPlayer(UUID uuid, String lastName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = dataSource.getConnection()) {
                    // Try update first (SQLite/MySQL compatible)
                    try (PreparedStatement updateStmt = conn.prepareStatement(
                         "UPDATE players SET last_name = ?, last_seen = ? WHERE uuid = ?")) {
                        long now = System.currentTimeMillis();
                        updateStmt.setString(1, lastName);
                        updateStmt.setLong(2, now);
                        updateStmt.setString(3, uuid.toString());
                        
                        int rows = updateStmt.executeUpdate();
                        if (rows > 0) {
                            // Updated existing record
                            Map<String, Object> kv = new HashMap<>();
                            kv.put("uuid", uuid.toString());
                            kv.put("lastName", lastName);
                            plugin.getDebugLog().debug(DebugLog.Tag.DATA, "PlayerDao", "Player updated", kv);
                            return;
                        }
                    }
                    
                    // If no rows updated, insert new record
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                         "INSERT INTO players (uuid, last_name, last_seen) VALUES (?, ?, ?)")) {
                        long now = System.currentTimeMillis();
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setString(2, lastName);
                        insertStmt.setLong(3, now);
                        
                        insertStmt.executeUpdate();
                        
                        Map<String, Object> kv = new HashMap<>();
                        kv.put("uuid", uuid.toString());
                        kv.put("lastName", lastName);
                        plugin.getDebugLog().debug(DebugLog.Tag.DATA, "PlayerDao", "Player inserted", kv);
                    }
                } catch (SQLException e) {
                    plugin.getDebugLog().error("PlayerDao", "Failed to upsert player", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
