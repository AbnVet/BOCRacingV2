package com.bocrace.command;

import com.bocrace.BOCRacingV2;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.CourseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BOCRaceCommand implements CommandExecutor, TabCompleter {

    private final BOCRacingV2 plugin;
    private final CourseCommandHandler courseHandler;

    public BOCRaceCommand(BOCRacingV2 plugin, CourseCommandHandler courseHandler) {
        this.plugin = plugin;
        this.courseHandler = courseHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            String version = plugin.getDescription().getVersion();
            sender.sendMessage("BOCRacingV2 v" + version);
            return true;
        }
        
        // Route to course handler
        return courseHandler.onCommand(sender, command, label, args);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return courseHandler.onTabComplete(sender, command, alias, args);
    }
}
