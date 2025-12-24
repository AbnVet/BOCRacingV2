package com.bocrace;

import com.bocrace.command.BOCRaceCommand;
import com.bocrace.command.CourseCommandHandler;
import com.bocrace.db.DatabaseManager;
import com.bocrace.db.DbDispatcher;
import com.bocrace.db.PlayerDao;
import com.bocrace.db.QueryDao;
import com.bocrace.db.RunDao;
import com.bocrace.listener.CourseButtonListener;
import com.bocrace.listener.PlayerLifecycleListener;
import com.bocrace.listener.SetupListener;
import com.bocrace.runtime.DropBlockManager;
import com.bocrace.runtime.RaceDetectionTask;
import com.bocrace.runtime.RaceManager;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.DebugLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BOCRacingV2 extends JavaPlugin {

    private SetupSessionManager setupSessionManager;
    private CourseManager courseManager;
    private RaceManager raceManager;
    private DropBlockManager dropBlockManager;
    private BukkitTask detectionTask;
    private DebugLog debugLog;
    private DatabaseManager databaseManager;
    private DbDispatcher dbDispatcher;
    private RunDao runDao;
    private PlayerDao playerDao;
    private QueryDao queryDao;

    @Override
    public void onEnable() {
        getLogger().info("BOCRacingV2 v" + getDescription().getVersion() + " has been enabled!");
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize debug logging
        this.debugLog = new DebugLog(this);
        
        // Initialize database
        this.databaseManager = new DatabaseManager(this);
        this.dbDispatcher = new DbDispatcher(this);
        try {
            databaseManager.initialize();
            this.runDao = new RunDao(this, databaseManager.getDataSource(), dbDispatcher);
            this.playerDao = new PlayerDao(this, databaseManager.getDataSource(), dbDispatcher);
            this.queryDao = new QueryDao(this, databaseManager.getDataSource(), dbDispatcher);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            // Continue without database - plugin should still function
        }
        
        // Initialize managers
        this.setupSessionManager = new SetupSessionManager();
        this.courseManager = new CourseManager(this);
        this.raceManager = new RaceManager();
        this.dropBlockManager = new DropBlockManager(this);
        
        // Create command handler
        CourseCommandHandler commandHandler = new CourseCommandHandler(this, setupSessionManager, courseManager);
        
        // Register main command
        BOCRaceCommand mainCommand = new BOCRaceCommand(this, commandHandler);
        getCommand("bocrace").setExecutor(mainCommand);
        getCommand("bocrace").setTabCompleter(mainCommand);
        
        // Register listeners
        getServer().getPluginManager().registerEvents(
            new SetupListener(this, setupSessionManager, courseManager), 
            this
        );
        getServer().getPluginManager().registerEvents(
            new CourseButtonListener(this, courseManager, raceManager),
            this
        );
        getServer().getPluginManager().registerEvents(
            new PlayerLifecycleListener(this, raceManager, courseManager),
            this
        );
        
        // Start race detection task (runs every 5 ticks)
        RaceDetectionTask detectionTaskRunnable = new RaceDetectionTask(this, raceManager, courseManager);
        this.detectionTask = detectionTaskRunnable.runTaskTimer(this, 0L, 5L);
    }

    @Override
    public void onDisable() {
        // Clear all setup sessions
        if (setupSessionManager != null) {
            setupSessionManager.clearAll();
        }
        
        // Cancel detection task
        if (detectionTask != null) {
            detectionTask.cancel();
        }
        
        // Clear all race state and drop blocks
        if (dropBlockManager != null) {
            dropBlockManager.clearAll();
        }
        if (raceManager != null) {
            raceManager.clearAll();
        }
        
        // Shutdown database dispatcher
        if (dbDispatcher != null) {
            dbDispatcher.shutdown();
        }
        
        // Close database
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        // Close debug log
        if (debugLog != null) {
            debugLog.close();
        }
        
        getLogger().info("BOCRacingV2 has been disabled!");
    }
    
    public SetupSessionManager getSetupSessionManager() {
        return setupSessionManager;
    }
    
    public CourseManager getCourseManager() {
        return courseManager;
    }
    
    public RaceManager getRaceManager() {
        return raceManager;
    }
    
    public DropBlockManager getDropBlockManager() {
        return dropBlockManager;
    }
    
    public DebugLog getDebugLog() {
        return debugLog;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public DbDispatcher getDbDispatcher() {
        return dbDispatcher;
    }
    
    public RunDao getRunDao() {
        return runDao;
    }
    
    public PlayerDao getPlayerDao() {
        return playerDao;
    }
    
    public QueryDao getQueryDao() {
        return queryDao;
    }
}
