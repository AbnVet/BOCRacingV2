package com.bocrace.db;

import com.bocrace.BOCRacingV2;
import com.bocrace.util.DebugLog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages database connections and migrations
 */
public class DatabaseManager {
    
    private final BOCRacingV2 plugin;
    private HikariDataSource dataSource;
    private boolean initialized;
    
    public DatabaseManager(BOCRacingV2 plugin) {
        this.plugin = plugin;
        this.initialized = false;
    }
    
    /**
     * Initialize database connection pool and run migrations
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        
        FileConfiguration config = plugin.getConfig();
        String dbType = config.getString("database.type", "SQLITE").toUpperCase();
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maxConnections", 10));
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setLeakDetectionThreshold(60000);
        
        String jdbcUrl;
        if ("MYSQL".equals(dbType)) {
            // MySQL configuration
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "bocracing");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "password");
            
            jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", 
                                   host, port, database);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            // SQLite configuration (default)
            File dataFolder = plugin.getDataFolder();
            String fileName = config.getString("database.sqlite.file", "bocracing.db");
            File dbFile = new File(dataFolder, fileName);
            
            jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            // SQLite-specific pool settings
            hikariConfig.setMaximumPoolSize(Math.min(config.getInt("database.pool.maxConnections", 10), 5));
        }
        
        try {
            dataSource = new HikariDataSource(hikariConfig);
            
            // Run Flyway migrations
            runMigrations();
            
            initialized = true;
            
            Map<String, Object> kv = new HashMap<>();
            kv.put("type", dbType);
            kv.put("jdbcUrl", maskPassword(jdbcUrl));
            plugin.getDebugLog().info(DebugLog.Tag.DATA, "DatabaseManager", "Database initialized", kv);
            
            plugin.getLogger().info("Database initialized: " + dbType);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            plugin.getDebugLog().error("DatabaseManager", "Database initialization failed", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Run Flyway migrations
     */
    private void runMigrations() {
        try {
            FluentConfiguration flywayConfig = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true);
            
            Flyway flyway = flywayConfig.load();
            
            // Get migration info before migration
            MigrationInfo[] pending = flyway.info().pending();
            int pendingCount = pending.length;
            
            // Run migrations
            var result = flyway.migrate();
            int applied = result.migrationsExecuted;
            
            Map<String, Object> kv = new HashMap<>();
            kv.put("applied", applied);
            kv.put("pending", pendingCount);
            plugin.getDebugLog().info(DebugLog.Tag.DATA, "DatabaseManager", "Migrations completed", kv);
            
            if (applied > 0) {
                plugin.getLogger().info("Applied " + applied + " database migration(s)");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to run database migrations: " + e.getMessage());
            plugin.getDebugLog().error("DatabaseManager", "Migration failed", e);
            throw new RuntimeException("Migration failed", e);
        }
    }
    
    /**
     * Get the datasource
     */
    public DataSource getDataSource() {
        if (!initialized) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return dataSource;
    }
    
    /**
     * Health check - test database connection
     */
    public boolean healthCheck() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(5); // 5 second timeout
        } catch (Exception e) {
            plugin.getDebugLog().error("DatabaseManager", "Health check failed", e);
            return false;
        }
    }
    
    /**
     * Close database connections
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getDebugLog().info(DebugLog.Tag.DATA, "DatabaseManager", "Database closed", null);
            plugin.getLogger().info("Database connections closed");
        }
        initialized = false;
    }
    
    /**
     * Mask password in JDBC URL for logging
     */
    private String maskPassword(String jdbcUrl) {
        return jdbcUrl.replaceAll("password=[^&;]+", "password=***");
    }
}
