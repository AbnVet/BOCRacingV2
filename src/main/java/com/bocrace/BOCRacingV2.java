package com.bocrace;

import com.bocrace.command.BOCRaceCommand;
import com.bocrace.command.CourseCommandHandler;
import com.bocrace.listener.SetupListener;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.CourseManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BOCRacingV2 extends JavaPlugin {

    private SetupSessionManager setupSessionManager;
    private CourseManager courseManager;

    @Override
    public void onEnable() {
        getLogger().info("BOCRacingV2 v" + getDescription().getVersion() + " has been enabled!");
        
        // Initialize managers
        this.setupSessionManager = new SetupSessionManager();
        this.courseManager = new CourseManager(this);
        
        // Create command handler
        CourseCommandHandler commandHandler = new CourseCommandHandler(this, setupSessionManager, courseManager);
        
        // Register main command
        BOCRaceCommand mainCommand = new BOCRaceCommand(this, commandHandler);
        getCommand("bocrace").setExecutor(mainCommand);
        getCommand("bocrace").setTabCompleter(mainCommand);
        
        // Register listener
        getServer().getPluginManager().registerEvents(
            new SetupListener(this, setupSessionManager, courseManager), 
            this
        );
    }

    @Override
    public void onDisable() {
        // Clear all setup sessions
        if (setupSessionManager != null) {
            setupSessionManager.clearAll();
        }
        
        getLogger().info("BOCRacingV2 has been disabled!");
    }
    
    public SetupSessionManager getSetupSessionManager() {
        return setupSessionManager;
    }
    
    public CourseManager getCourseManager() {
        return courseManager;
    }
}
