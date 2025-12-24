package com.bocrace.command;

import com.bocrace.BOCRacingV2;
import com.bocrace.db.QueryDao;
import com.bocrace.model.CourseType;
import com.bocrace.model.Course;
import com.bocrace.setup.SetupSession;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.CourseValidator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
            case "validate":
                return handleValidate(sender, args);
            case "stats":
                return handleStats(sender, args);
            case "player":
                return handlePlayer(sender, args);
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
        sender.sendMessage("§a/bocrace validate <name> §7- Check course validation");
        sender.sendMessage("§a/bocrace cancel §7- Cancel current armed action");
        sender.sendMessage("§a/bocrace stats <course> §7- Show top times for a course");
        sender.sendMessage("§a/bocrace player <name|uuid> [course] §7- Show player stats");
        sender.sendMessage("§7Actions: player_spawn, course_lobby, start, finish, checkpoint");
        sender.sendMessage("§7Note: Courses are saved immediately. Incomplete courses are blocked from use.");
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
        
        // Check if course already exists (by display name)
        if (courseManager.courseExists(courseName)) {
            sender.sendMessage("§cCourse '" + courseName + "' already exists!");
            return true;
        }
        
        // Check for filename collision in target folder
        String safeFileName = CourseManager.toSafeFileName(courseName);
        File targetFile = courseManager.getCourseFile(type, safeFileName);
        if (targetFile.exists()) {
            sender.sendMessage("§cA course with filename '" + safeFileName + ".yml' already exists in " + 
                             (type == CourseType.BOAT ? "boatracing" : "airracing") + " folder!");
            sender.sendMessage("§7Please use a different course name.");
            return true;
        }
        
        // Create course
        Course course = new Course(courseName, type);
        
        try {
            courseManager.saveCourse(course);
            sender.sendMessage("§aCourse '" + courseName + "' created!");
            sender.sendMessage("§7Type: " + type);
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
            sender.sendMessage("§7Solo: solo_join_button, solo_return_button");
            sender.sendMessage("§7Multi: mp_lobby, mp_join_button, mp_leader_create_button, mp_leader_start_button, mp_leader_cancel_button");
            return true;
        }
        
        String courseName = args[1];
        String actionStr = args[2].toLowerCase();
        
        // Find course (searches both folders)
        Course course = courseManager.findCourse(courseName);
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
            case "solo_join_button":
                action = SetupSession.ArmedAction.SOLO_JOIN_BUTTON;
                break;
            case "solo_return_button":
                action = SetupSession.ArmedAction.SOLO_RETURN_BUTTON;
                break;
            case "mp_lobby":
                action = SetupSession.ArmedAction.MP_LOBBY;
                break;
            case "mp_join_button":
                action = SetupSession.ArmedAction.MP_JOIN_BUTTON;
                break;
            case "mp_leader_create_button":
                action = SetupSession.ArmedAction.MP_LEADER_CREATE_BUTTON;
                break;
            case "mp_leader_start_button":
                action = SetupSession.ArmedAction.MP_LEADER_START_BUTTON;
                break;
            case "mp_leader_cancel_button":
                action = SetupSession.ArmedAction.MP_LEADER_CANCEL_BUTTON;
                break;
            default:
                sender.sendMessage("§cUnknown action: " + actionStr);
                sender.sendMessage("§7Valid actions: player_spawn, course_lobby, start, finish, checkpoint");
                sender.sendMessage("§7Solo: solo_join_button, solo_return_button");
                sender.sendMessage("§7Multi: mp_lobby, mp_join_button, mp_leader_create_button, mp_leader_start_button, mp_leader_cancel_button");
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
        
        Course course = courseManager.findCourse(courseName);
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
        sender.sendMessage("§7Type: §f" + course.getType());
        sender.sendMessage("§7File: §f" + filePath);
        sender.sendMessage("");
        
        // Settings
        Course.CourseSettings settings = course.getSettings();
        if (settings == null) {
            settings = new Course.CourseSettings(); // Ensure defaults
        }
        sender.sendMessage("§6Settings:");
        sender.sendMessage("  §7Start Mode: §f" + settings.getStartMode().name());
        sender.sendMessage("  §7Countdown: §f" + settings.getCountdownSeconds() + " seconds");
        sender.sendMessage("  §7Solo Cooldown: §f" + settings.getSoloCooldownSeconds() + " seconds");
        if (settings.getStartMode() == Course.StartMode.DROP_START) {
            sender.sendMessage("  §7Drop Radius: §f" + settings.getDrop().getRadius() + " blocks");
            sender.sendMessage("  §7Drop Restore: §f" + settings.getDrop().getRestoreSeconds() + " seconds");
        }
        sender.sendMessage("");
        
        // Checklist
        boolean startSet = course.getStartRegion() != null && 
                          course.getStartRegion().getMin() != null && 
                          course.getStartRegion().getMax() != null;
        boolean finishSet = course.getFinishRegion() != null && 
                           course.getFinishRegion().getMin() != null && 
                           course.getFinishRegion().getMax() != null;
        
        // Check for world-not-loaded issues
        boolean startWorldMissing = false;
        boolean finishWorldMissing = false;
        if (course.getStartRegion() != null && course.getStartRegion().getWorld() != null) {
            startWorldMissing = Bukkit.getWorld(course.getStartRegion().getWorld()) == null;
        }
        if (course.getFinishRegion() != null && course.getFinishRegion().getWorld() != null) {
            finishWorldMissing = Bukkit.getWorld(course.getFinishRegion().getWorld()) == null;
        }
        
        sender.sendMessage("§6Checklist:");
        String lobbyStatus = course.getCourseLobbySpawn() != null ? "§aSET" : "§cMISSING";
        // Note: If location is null, we can't distinguish "never set" from "world not loaded"
        // Locations are only created when world is loaded, so if it exists, world should be non-null
        boolean lobbyValid = course.getCourseLobbySpawn() != null && course.getCourseLobbySpawn().getWorld() != null;
        sender.sendMessage("  " + (lobbyValid ? "§a✓" : "§c✗") + " §7Course Lobby: " + lobbyStatus);
        
        String spawnStatus = spawnCount + " §7(" + mode + "§7)";
        sender.sendMessage("  " + (spawnCount > 0 ? "§a✓" : "§c✗") + " §7Player Spawns: §f" + spawnStatus);
        
        String startStatus = startSet ? "§aSET" : "§cMISSING";
        if (startWorldMissing) {
            startStatus = "§cMISSING (World '" + course.getStartRegion().getWorld() + "' not loaded)";
        }
        sender.sendMessage("  " + (startSet && !startWorldMissing ? "§a✓" : "§c✗") + " §7Start Line: " + startStatus);
        
        String finishStatus = finishSet ? "§aSET" : "§cMISSING";
        if (finishWorldMissing) {
            finishStatus = "§cMISSING (World '" + course.getFinishRegion().getWorld() + "' not loaded)";
        }
        sender.sendMessage("  " + (finishSet && !finishWorldMissing ? "§a✓" : "§c✗") + " §7Finish Line: " + finishStatus);
        sender.sendMessage("  " + (course.getCheckpoints().size() > 0 ? "§a✓" : "§7○") + " §7Checkpoints: §f" + course.getCheckpoints().size());
        
        // Validation status
        CourseValidator.ValidationResult validation = CourseValidator.validate(course);
        boolean courseReady = validation.isOk();
        
        sender.sendMessage("");
        sender.sendMessage("§6Validation:");
        sender.sendMessage("  §7Course Ready: " + (courseReady ? "§aYES" : "§cNO"));
        if (!courseReady && !validation.getIssues().isEmpty()) {
            sender.sendMessage("  §7Issue: §c" + validation.getIssues().get(0));
        }
        
        // Next step (single command)
        sender.sendMessage("");
        sender.sendMessage("§6Next Step:");
        if (course.getCourseLobbySpawn() == null) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " course_lobby");
        } else if (spawnCount == 0) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " player_spawn");
        } else if (!startSet) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " start");
        } else if (!finishSet) {
            sender.sendMessage("  §7→ §a/bocrace setup " + courseName + " finish");
        } else if (!courseReady) {
            sender.sendMessage("  §7→ §a/bocrace validate " + courseName);
        } else {
            sender.sendMessage("  §7→ §aCourse complete and ready for use");
        }
        
        return true;
    }
    
    private boolean handleValidate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            sender.sendMessage("§cYou don't have permission to validate courses!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bocrace validate <name>");
            return true;
        }
        
        String courseName = args[1];
        
        Course course = courseManager.findCourse(courseName);
        if (course == null) {
            sender.sendMessage("§cCourse '" + courseName + "' not found!");
            return true;
        }
        
        // Run validation (read-only, no status changes)
        CourseValidator.ValidationResult result = CourseValidator.validate(course);
        
        if (!result.isOk()) {
            // FAIL: show issues
            sender.sendMessage("§cValidation FAILED:");
            List<String> issues = result.getIssues();
            for (int i = 0; i < issues.size(); i++) {
                sender.sendMessage("§c  " + (i + 1) + ". " + issues.get(i));
            }
            return true;
        }
        
        // PASS: show success (no status change, no save)
        sender.sendMessage("§aValidation PASSED.");
        sender.sendMessage("§7Course is ready for use.");
        
        return true;
    }
    
    private boolean handleStats(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "bocrace.admin")) {
            sender.sendMessage("§cYou don't have permission to view stats!");
            return true;
        }
        
        if (plugin.getQueryDao() == null) {
            sender.sendMessage("§cDatabase is not available.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bocrace stats <course>");
            return true;
        }
        
        String courseName = args[1];
        Course course = courseManager.findCourse(courseName);
        if (course == null) {
            sender.sendMessage("§cCourse not found: " + courseName);
            return true;
        }
        
        String courseKey = course.getName();
        Future<List<QueryDao.TopTime>> future = plugin.getQueryDao().getTopTimes(courseKey, 10);
        
        // Wait for result (with timeout)
        sender.sendMessage("§7Loading top times for §e" + courseKey + "§7...");
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<QueryDao.TopTime> topTimes = future.get(5, TimeUnit.SECONDS);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§6=== Top Times: " + courseKey + " ===");
                    if (topTimes.isEmpty()) {
                        sender.sendMessage("§7No finished runs yet.");
                    } else {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        for (int i = 0; i < topTimes.size(); i++) {
                            QueryDao.TopTime entry = topTimes.get(i);
                            String timeStr = formatTime(entry.getDurationMillis());
                            String dateStr = dateFormat.format(new Date(entry.getFinishMillis()));
                            sender.sendMessage("§e" + (i + 1) + ". §f" + entry.getPlayerName() + " §7- §a" + timeStr + " §7(" + dateStr + ")");
                        }
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get top times: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cFailed to load stats.");
                });
            }
        });
        
        return true;
    }
    
    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "bocrace.admin")) {
            sender.sendMessage("§cYou don't have permission to view player stats!");
            return true;
        }
        
        if (plugin.getQueryDao() == null) {
            sender.sendMessage("§cDatabase is not available.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bocrace player <name|uuid> [course]");
            return true;
        }
        
        String identifier = args[1];
        UUID playerUuid = null;
        
        // Try to resolve UUID
        try {
            playerUuid = UUID.fromString(identifier);
        } catch (IllegalArgumentException e) {
            // Try player name
            OfflinePlayer player = Bukkit.getOfflinePlayer(identifier);
            if (player.hasPlayedBefore() || player.isOnline()) {
                playerUuid = player.getUniqueId();
            } else {
                sender.sendMessage("§cPlayer not found: " + identifier);
                return true;
            }
        }
        
        if (args.length >= 3) {
            // Show best time for specific course
            String courseName = args[2];
            Course course = courseManager.findCourse(courseName);
            if (course == null) {
                sender.sendMessage("§cCourse not found: " + courseName);
                return true;
            }
            
            String courseKey = course.getName();
            Future<QueryDao.TopTime> future = plugin.getQueryDao().getPlayerBest(courseKey, playerUuid);
            
            sender.sendMessage("§7Loading best time for §e" + identifier + " §7on §e" + courseKey + "§7...");
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    QueryDao.TopTime best = future.get(5, TimeUnit.SECONDS);
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§6=== Best Time: " + identifier + " ===");
                        sender.sendMessage("§7Course: §e" + courseKey);
                        if (best == null) {
                            sender.sendMessage("§7No finished runs yet.");
                        } else {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                            String timeStr = formatTime(best.getDurationMillis());
                            String dateStr = dateFormat.format(new Date(best.getFinishMillis()));
                            sender.sendMessage("§aBest: §f" + timeStr + " §7(" + dateStr + ")");
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to get player best: " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§cFailed to load stats.");
                    });
                }
            });
        } else {
            // Show recent runs
            Future<List<QueryDao.PlayerRun>> future = plugin.getQueryDao().getPlayerRecentRuns(playerUuid, 10);
            
            sender.sendMessage("§7Loading recent runs for §e" + identifier + "§7...");
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<QueryDao.PlayerRun> runs = future.get(5, TimeUnit.SECONDS);
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§6=== Recent Runs: " + identifier + " ===");
                        if (runs.isEmpty()) {
                            sender.sendMessage("§7No runs found.");
                        } else {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                            for (int i = 0; i < runs.size(); i++) {
                                QueryDao.PlayerRun run = runs.get(i);
                                String statusColor = run.getStatus().equals("FINISHED") ? "§a" : 
                                                    run.getStatus().equals("ABORTED") ? "§c" : "§7";
                                String statusStr = statusColor + run.getStatus();
                                String courseKey = run.getCourseKey();
                                
                                if (run.getStatus().equals("FINISHED")) {
                                    String timeStr = formatTime(run.getDurationMillis());
                                    String dateStr = dateFormat.format(new Date(run.getFinishMillis()));
                                    sender.sendMessage("§e" + (i + 1) + ". §f" + courseKey + " §7- §a" + timeStr + " §7(" + dateStr + ") " + statusStr);
                                } else {
                                    sender.sendMessage("§e" + (i + 1) + ". §f" + courseKey + " §7- " + statusStr);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to get player recent runs: " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§cFailed to load stats.");
                    });
                }
            });
        }
        
        return true;
    }
    
    /**
     * Format milliseconds as mm:ss.SSS
     */
    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long milliseconds = millis % 1000;
        
        return String.format("%02d:%02d.%03d", minutes, seconds, milliseconds);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Check permission
        if (!hasPermission(sender, "bocrace.admin") && !hasPermission(sender, "bocrace.builder")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("create", "setup", "status", "validate", "cancel", "stats", "player").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                return Arrays.asList("boatrace", "airrace").stream()
                    .filter(type -> type.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("setup") || subCommand.equals("status") || 
                       subCommand.equals("validate") || subCommand.equals("stats")) {
                // Complete course names
                return courseManager.listAllCourses().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("player")) {
                // Complete player names (only online players for simplicity)
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("setup")) {
                return Arrays.asList("player_spawn", "course_lobby", "start", "finish", "checkpoint",
                    "solo_join_button", "solo_return_button", "mp_lobby", "mp_join_button",
                    "mp_leader_create_button", "mp_leader_start_button", "mp_leader_cancel_button").stream()
                    .filter(action -> action.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("player") && args.length == 3) {
                // Complete course names for player command
                return courseManager.listAllCourses().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
}
