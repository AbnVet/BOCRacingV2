package com.bocrace;

import com.bocrace.command.AdminCommand;
import com.bocrace.command.BOCRaceCommand;
import com.bocrace.listener.SetupListener;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.DraftManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BOCRacingV2 extends JavaPlugin {

    private SetupSessionManager setupSessionManager;
    private DraftManager draftManager;

    @Override
    public void onEnable() {
        getLogger().info("BOCRacingV2 v" + getDescription().getVersion() + " has been enabled!");
        
        // Initialize managers
        this.setupSessionManager = new SetupSessionManager();
        this.draftManager = new DraftManager(this);
        
        // Create admin command
        AdminCommand adminCommand = new AdminCommand(this, setupSessionManager, draftManager);
        
        // Register main command (routes to admin command for admin subcommands)
        BOCRaceCommand mainCommand = new BOCRaceCommand(this, adminCommand);
        getCommand("bocrace").setExecutor(mainCommand);
        getCommand("bocrace").setTabCompleter(mainCommand);
        
        // Register listener
        getServer().getPluginManager().registerEvents(
            new SetupListener(this, setupSessionManager, draftManager), 
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
    
    public DraftManager getDraftManager() {
        return draftManager;
    }
}
