package com.bocrace.command;

import com.bocrace.BOCRacingV2;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BOCRaceCommand implements CommandExecutor {

    private final BOCRacingV2 plugin;

    public BOCRaceCommand(BOCRacingV2 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String version = plugin.getDescription().getVersion();
        sender.sendMessage("BOCRacingV2 v" + version);
        return true;
    }
}
