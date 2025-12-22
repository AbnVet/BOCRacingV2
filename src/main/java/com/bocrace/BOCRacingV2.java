package com.bocrace;

import com.bocrace.command.BOCRaceCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BOCRacingV2 extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("BOCRacingV2 v" + getDescription().getVersion() + " has been enabled!");
        
        // Register command
        getCommand("bocrace").setExecutor(new BOCRaceCommand(this));
    }

    @Override
    public void onDisable() {
        getLogger().info("BOCRacingV2 has been disabled!");
    }
}
