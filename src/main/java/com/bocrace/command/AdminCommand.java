package com.bocrace.command;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.CourseStatus;
import com.bocrace.model.CourseType;
import com.bocrace.model.DraftCourse;
import com.bocrace.setup.SetupSession;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.DraftManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /bocrace admin commands
 */
public class AdminCommand implements CommandExecutor, TabCompleter {
    
    private final BOCRacingV2 plugin;
    private final SetupSessionManager sessionManager;
    private final DraftManager draftManager;
    
    public AdminCommand(BOCRacingV2 plugin, SetupSessionManager sessionManager, DraftManager draftManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.draftManager = draftManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (!sender.hasPermission("bocrace.admin")) {
            sender.sendMessage("§cYou don't have permission to use admin commands!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        Player player = (Player) sender;
        
        switch (subCommand) {
            case "create":
                return handleCreate(player, args);
            case "setup":
                return handleSetup(player, args);
            case "cancel":
                return handleCancel(player);
            case "status":
                return handleStatus(player, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== BOCRacingV2 Admin Commands ===");
        sender.sendMessage("§a/bocrace admin create <name> <type> §7- Create a draft course");
        sender.sendMessage("§a/bocrace admin setup <name> <action> §7- Arm a setup action");
        sender.sendMessage("§a/bocrace admin cancel §7- Cancel current armed action");
        sender.sendMessage("§a/bocrace admin status <name> §7- Show course setup progress");
        sender.sendMessage("§7Actions: player_spawn, course_lobby, start, finish, checkpoint");
    }
    
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /bocrace admin create <name> <type>");
            player.sendMessage("§7Types: BOAT, AIR");
            return true;
        }
        
        String courseName = args[1];
        String typeStr = args[2].toUpperCase();
        
        // Validate type
        CourseType type;
        try {
            type = CourseType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid type. Use BOAT or AIR");
            return true;
        }
        
        // Check if draft already exists
        if (draftManager.draftExists(courseName)) {
            player.sendMessage("§cDraft course '" + courseName + "' already exists!");
            return true;
        }
        
        // Create draft
        DraftCourse draft = new DraftCourse(courseName, type);
        draft.setStatus(CourseStatus.DRAFT);
        
        try {
            draftManager.saveDraft(draft);
            player.sendMessage("§aDraft course '" + courseName + "' created!");
            player.sendMessage("§7Type: " + type);
            player.sendMessage("§7Status: DRAFT");
            player.sendMessage("§7Use /bocrace admin setup " + courseName + " <action> to configure");
        } catch (IOException e) {
            player.sendMessage("§cFailed to save draft: " + e.getMessage());
            plugin.getLogger().severe("Failed to save draft: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleSetup(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /bocrace admin setup <name> <action>");
            player.sendMessage("§7Actions: player_spawn, course_lobby, start, finish, checkpoint");
            return true;
        }
        
        String courseName = args[1];
        String actionStr = args[2].toLowerCase();
        
        // Load draft
        DraftCourse draft;
        try {
            draft = draftManager.loadDraft(courseName);
            if (draft == null) {
                player.sendMessage("§cDraft course '" + courseName + "' not found!");
                player.sendMessage("§7Create it first with /bocrace admin create " + courseName + " <type>");
                return true;
            }
        } catch (IOException e) {
            player.sendMessage("§cFailed to load draft: " + e.getMessage());
            return true;
        }
        
        // Map action string to ArmedAction
        SetupSession.ArmedAction action;
        switch (actionStr) {
            case "player_spawn":
                action = SetupSession.ArmedAction.PLAYER_SPAWN;
                break;
            case "course_lobby":
                action = SetupSession.ArmedAction.COURSE_LOBBY;
                break;
            case "start":
                action = SetupSession.ArmedAction.START;
                break;
            case "finish":
                action = SetupSession.ArmedAction.FINISH;
                break;
            case "checkpoint":
                action = SetupSession.ArmedAction.CHECKPOINT;
                break;
            default:
                player.sendMessage("§cUnknown action: " + actionStr);
                player.sendMessage("§7Valid actions: player_spawn, course_lobby, start, finish, checkpoint");
                return true;
        }
        
        // Start session
        sessionManager.startSession(player, courseName, action);
        
        // Provide feedback
        switch (action) {
            case PLAYER_SPAWN:
                player.sendMessage("§aArmed: PLAYER_SPAWN capture");
                player.sendMessage("§7Right-click a block to set spawn #" + (draft.getPlayerSpawns().size() + 1));
                break;
            case COURSE_LOBBY:
                player.sendMessage("§aArmed: COURSE_LOBBY capture");
                player.sendMessage("§7Right-click a block to set course lobby spawn");
                break;
            case START:
                player.sendMessage("§aArmed: START region capture");
                player.sendMessage("§7Right-click block 1 of 2 for start line");
                break;
            case FINISH:
                player.sendMessage("§aArmed: FINISH region capture");
                player.sendMessage("§7Right-click block 1 of 2 for finish line");
                break;
            case CHECKPOINT:
                player.sendMessage("§aArmed: CHECKPOINT region capture");
                player.sendMessage("§7Right-click block 1 of 2 for checkpoint #" + (draft.getCheckpoints().size() + 1));
                break;
        }
        player.sendMessage("§7Use /bocrace admin cancel to disarm");
        
        return true;
    }
    
    private boolean handleCancel(Player player) {
        if (!sessionManager.hasSession(player)) {
            player.sendMessage("§cYou don't have an armed action to cancel!");
            return true;
        }
        
        sessionManager.clearSession(player);
        player.sendMessage("§aArmed action cancelled");
        
        return true;
    }
    
    private boolean handleStatus(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /bocrace admin status <name>");
            return true;
        }
        
        String courseName = args[1];
        
        DraftCourse draft;
        try {
            draft = draftManager.loadDraft(courseName);
            if (draft == null) {
                player.sendMessage("§cDraft course '" + courseName + "' not found!");
                return true;
            }
        } catch (IOException e) {
            player.sendMessage("§cFailed to load draft: " + e.getMessage());
            return true;
        }
        
        // Show status
        player.sendMessage("§6=== Course Setup Status: " + courseName + " ===");
        player.sendMessage("§7Type: §f" + draft.getType());
        player.sendMessage("§7Status: §f" + draft.getStatus());
        
        // Show progress
        player.sendMessage("§6Setup Progress:");
        player.sendMessage("§7Course Lobby: " + (draft.getCourseLobbySpawn() != null ? "§aSET" : "§cNOT SET"));
        player.sendMessage("§7Player Spawns: §f" + draft.getPlayerSpawns().size());
        player.sendMessage("§7Start Line: " + (draft.getStartPoint1() != null && draft.getStartPoint2() != null ? "§aSET" : "§cNOT SET"));
        player.sendMessage("§7Finish Line: " + (draft.getFinishPoint1() != null && draft.getFinishPoint2() != null ? "§aSET" : "§cNOT SET"));
        player.sendMessage("§7Checkpoints: §f" + draft.getCheckpoints().size());
        
        // Suggest next step
        player.sendMessage("§6Next Steps:");
        if (draft.getCourseLobbySpawn() == null) {
            player.sendMessage("§7→ /bocrace admin setup " + courseName + " course_lobby");
        } else if (draft.getPlayerSpawns().isEmpty()) {
            player.sendMessage("§7→ /bocrace admin setup " + courseName + " player_spawn");
        } else if (draft.getStartPoint1() == null || draft.getStartPoint2() == null) {
            player.sendMessage("§7→ /bocrace admin setup " + courseName + " start");
        } else if (draft.getFinishPoint1() == null || draft.getFinishPoint2() == null) {
            player.sendMessage("§7→ /bocrace admin setup " + courseName + " finish");
        } else {
            player.sendMessage("§7→ Course setup complete! Add checkpoints or validate.");
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bocrace.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("create", "setup", "cancel", "status").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                // No completion for course name
                return new ArrayList<>();
            } else if (subCommand.equals("setup") || subCommand.equals("status")) {
                // Complete draft course names
                return draftManager.listDrafts().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                return Arrays.asList("BOAT", "AIR").stream()
                    .filter(type -> type.startsWith(args[2].toUpperCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("setup")) {
                return Arrays.asList("player_spawn", "course_lobby", "start", "finish", "checkpoint").stream()
                    .filter(action -> action.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
}
