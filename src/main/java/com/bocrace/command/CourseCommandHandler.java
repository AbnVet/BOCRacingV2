package com.bocrace.command;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.CourseStatus;
import com.bocrace.model.CourseType;
import com.bocrace.model.DraftCourse;
import com.bocrace.setup.SetupSession;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.CourseManager;
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
 * Handles /bocrace commands (create, setup, status, cancel)
 */
public class CourseCommandHandler implements CommandExecutor, TabCompleter {
    
    private final BOCRacingV2 plugin;
    private final SetupSessionManager sessionManager;
    private final CourseManager courseManager;
    
    public CourseCommandHandler(BOCRacingV2 plugin, SetupSessionManager sessionManager, CourseManager courseManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.courseManager = courseManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "setup":
                return handleSetup(sender, args);
            case "status":
                return handleStatus(sender, args);
            case "cancel":
                return handleCancel(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== BOCRacingV2 Commands ===");
        sender.sendMessage("§a/bocrace create <boatrace|airrace> <name> §7- Create a course");
        sender.sendMessage("§a/bocrace setup <name> <action> §7- Arm a setup action");
        sender.sendMessage("§a/bocrace status <name> §7- Show course setup progress");
        sender.sendMessage("§a/bocrace cancel §7- Cancel current armed action");
        sender.sendMessage("§7Actions: player_spawn, course_lobby, start, finish, checkpoint");
    }
    
    /**
     * Check if sender has permission (OP bypass)
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.isOp()) {
            return true;
        }
        return sender.hasPermission(permission);
    }
    
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            sender.sendMessage("§cYou don't have permission to create courses!");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /bocrace create <boatrace|airrace> <name>");
            return true;
        }
        
        String typeStr = args[1].toLowerCase();
        String courseName = args[2];
        
        // Validate type
        CourseType type;
        if (typeStr.equals("boatrace")) {
            type = CourseType.BOAT;
        } else if (typeStr.equals("airrace")) {
            type = CourseType.AIR;
        } else {
            sender.sendMessage("§cInvalid type. Use 'boatrace' or 'airrace'");
            return true;
        }
        
        // Check if course already exists
        if (courseManager.courseExists(courseName)) {
            sender.sendMessage("§cCourse '" + courseName + "' already exists!");
            return true;
        }
        
        // Create course
        DraftCourse course = new DraftCourse(courseName, type);
        course.setStatus(CourseStatus.DRAFT);
        
        try {
            courseManager.saveCourse(course);
            sender.sendMessage("§aCourse '" + courseName + "' created!");
            sender.sendMessage("§7Type: " + type);
            sender.sendMessage("§7Status: DRAFT");
            sender.sendMessage("§7Use /bocrace setup " + courseName + " <action> to configure");
        } catch (IOException e) {
            sender.sendMessage("§cFailed to save course: " + e.getMessage());
            plugin.getLogger().severe("Failed to save course: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleSetup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            sender.sendMessage("§cYou don't have permission to setup courses!");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /bocrace setup <name> <action>");
            sender.sendMessage("§7Actions: player_spawn, course_lobby, start, finish, checkpoint");
            return true;
        }
        
        String courseName = args[1];
        String actionStr = args[2].toLowerCase();
        
        // Find course (searches both folders)
        DraftCourse course = courseManager.findCourse(courseName);
        if (course == null) {
            sender.sendMessage("§cCourse '" + courseName + "' not found!");
            sender.sendMessage("§7Create it first with /bocrace create <boatrace|airrace> " + courseName);
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
                sender.sendMessage("§cUnknown action: " + actionStr);
                sender.sendMessage("§7Valid actions: player_spawn, course_lobby, start, finish, checkpoint");
                return true;
        }
        
        // Start session
        Player player = (Player) sender;
        sessionManager.startSession(player, courseName, action);
        
        // Provide feedback
        switch (action) {
            case PLAYER_SPAWN:
                player.sendMessage("§aArmed: PLAYER_SPAWN capture");
                player.sendMessage("§7Right-click a block to set spawn #" + (course.getPlayerSpawns().size() + 1));
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
                player.sendMessage("§7Right-click block 1 of 2 for checkpoint #" + (course.getCheckpoints().size() + 1));
                break;
        }
        player.sendMessage("§7Use /bocrace cancel to disarm");
        
        return true;
    }
    
    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        if (!sessionManager.hasSession(player)) {
            player.sendMessage("§cYou don't have an armed action to cancel!");
            return true;
        }
        
        sessionManager.clearSession(player);
        player.sendMessage("§aArmed action cancelled");
        
        return true;
    }
    
    private boolean handleStatus(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            sender.sendMessage("§cYou don't have permission to view course status!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bocrace status <name>");
            return true;
        }
        
        String courseName = args[1];
        
        DraftCourse course = courseManager.findCourse(courseName);
        if (course == null) {
            sender.sendMessage("§cCourse '" + courseName + "' not found!");
            return true;
        }
        
        // Get file path
        String safeFileName = CourseManager.toSafeFileName(courseName);
        String folderName = course.getType() == CourseType.BOAT ? "boatracing" : "airracing";
        String filePath = "plugins/BOCRacingV2/" + folderName + "/" + safeFileName + ".yml";
        
        // Derive mode from spawn count
        int spawnCount = course.getPlayerSpawns().size();
        String mode;
        if (spawnCount == 0) {
            mode = "§cUNSET";
        } else if (spawnCount == 1) {
            mode = "§eSOLO";
        } else {
            mode = "§bMULTI";
        }
        
        // Show status
        sender.sendMessage("§6═══ Course Status: " + course.getName() + " ═══");
        sender.sendMessage("§7Type: §f" + course.getType() + " §7│ Status: §f" + course.getStatus());
        sender.sendMessage("§7File: §f" + filePath);
        sender.sendMessage("");
        
        // Checklist
        sender.sendMessage("§6Checklist:");
        sender.sendMessage("  " + (course.getCourseLobbySpawn() != null ? "§a✓" : "§c✗") + " §7Course Lobby: " + (course.getCourseLobbySpawn() != null ? "§aSET" : "§cMISSING"));
        sender.sendMessage("  " + (spawnCount > 0 ? "§a✓" : "§c✗") + " §7Player Spawns: §f" + spawnCount + " §7(" + mode + "§7)");
        sender.sendMessage("  " + (course.getStartPoint1() != null && course.getStartPoint2() != null ? "§a✓" : "§c✗") + " §7Start Line: " + (course.getStartPoint1() != null && course.getStartPoint2() != null ? "§aSET" : "§cMISSING"));
        sender.sendMessage("  " + (course.getFinishPoint1() != null && course.getFinishPoint2() != null ? "§a✓" : "§c✗") + " §7Finish Line: " + (course.getFinishPoint1() != null && course.getFinishPoint2() != null ? "§aSET" : "§cMISSING"));
        sender.sendMessage("  " + (course.getCheckpoints().size() > 0 ? "§a✓" : "§7○") + " §7Checkpoints: §f" + course.getCheckpoints().size());
        
        // Next step (single command)
        sender.sendMessage("");
        sender.sendMessage("§6Next Step:");
        if (course.getCourseLobbySpawn() == null) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " course_lobby");
        } else if (spawnCount == 0) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " player_spawn");
        } else if (course.getStartPoint1() == null || course.getStartPoint2() == null) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " start");
        } else if (course.getFinishPoint1() == null || course.getFinishPoint2() == null) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " finish");
        } else {
            sender.sendMessage("  §7→ §aStructurally complete. Add checkpoints if desired.");
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Check permission
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("create", "setup", "status", "cancel").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                return Arrays.asList("boatrace", "airrace").stream()
                    .filter(type -> type.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("setup") || subCommand.equals("status")) {
                // Complete course names
                return courseManager.listAllCourses().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("setup")) {
                return Arrays.asList("player_spawn", "course_lobby", "start", "finish", "checkpoint").stream()
                    .filter(action -> action.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
}
