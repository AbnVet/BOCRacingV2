package com.bocrace.command;

import com.bocrace.BOCRacingV2;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BOCRaceCommand implements CommandExecutor, TabCompleter {

    private final BOCRacingV2 plugin;
    private final AdminCommand adminCommand;

    public BOCRaceCommand(BOCRacingV2 plugin, AdminCommand adminCommand) {
        this.plugin = plugin;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            String version = plugin.getDescription().getVersion();
            sender.sendMessage("BOCRacingV2 v" + version);
            return true;
        }
        
        // Route admin subcommands
        if (args[0].equalsIgnoreCase("admin")) {
            // Remove "admin" from args and pass to AdminCommand
            String[] adminArgs = new String[args.length - 1];
            System.arraycopy(args, 1, adminArgs, 0, adminArgs.length);
            return adminCommand.onCommand(sender, command, label, adminArgs);
        }
        
        // Default: show version
        String version = plugin.getDescription().getVersion();
        sender.sendMessage("BOCRacingV2 v" + version);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("admin").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length > 1 && args[0].equalsIgnoreCase("admin")) {
            // Remove "admin" from args and pass to AdminCommand
            String[] adminArgs = new String[args.length - 1];
            System.arraycopy(args, 1, adminArgs, 0, adminArgs.length);
            return adminCommand.onTabComplete(sender, command, alias, adminArgs);
        }
        
        return new ArrayList<>();
    }
}
