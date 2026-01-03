package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.runtime.DropBlockManager;
import com.bocrace.runtime.RaceManager;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.CourseValidator;
import com.bocrace.util.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles button clicks for race joining/starting/cancelling
 */
public class CourseButtonListener implements Listener {
    
    private final BOCRacingV2 plugin;
    private final CourseManager courseManager;
    private final RaceManager raceManager;
    private final DropBlockManager dropBlockManager;
    
    public CourseButtonListener(BOCRacingV2 plugin, CourseManager courseManager, RaceManager raceManager) {
        this.plugin = plugin;
        this.courseManager = courseManager;
        this.raceManager = raceManager;
        this.dropBlockManager = plugin.getDropBlockManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // CRITICAL: Ignore if player is in setup mode (prevents button clicks during setup)
        // SetupListener runs at HIGHEST priority, so this is a safety check
        if (plugin.getSetupSessionManager().hasSession(player)) {
            return; // SetupListener will handle this click
        }
        
        // CRITICAL: Cancel event if it was already handled by SetupListener
        if (event.isCancelled()) {
            return;
        }
        
        // Only handle right-click on blocks, main hand
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Location blockLoc = event.getClickedBlock().getLocation();
        String worldName = blockLoc.getWorld().getName();
        int blockX = blockLoc.getBlockX();
        int blockY = blockLoc.getBlockY();
        int blockZ = blockLoc.getBlockZ();
        
        // Find which course button was clicked
        Course clickedCourse = null;
        String buttonType = null;
        
        // Check all courses for button matches
        List<String> allCourses = courseManager.listAllCourses();
        for (String courseName : allCourses) {
            Course course = courseManager.findCourse(courseName);
            if (course == null) continue;
            
            // Check solo buttons
            if (course.getSoloJoinButton() != null && matchesButton(course.getSoloJoinButton(), worldName, blockX, blockY, blockZ)) {
                clickedCourse = course;
                buttonType = "solo_join";
                break;
            }
            if (course.getSoloReturnButton() != null && matchesButton(course.getSoloReturnButton(), worldName, blockX, blockY, blockZ)) {
                clickedCourse = course;
                buttonType = "solo_return";
                break;
            }
            
            // Check multi buttons
            if (course.getMpJoinButton() != null && matchesButton(course.getMpJoinButton(), worldName, blockX, blockY, blockZ)) {
                clickedCourse = course;
                buttonType = "mp_join";
                break;
            }
            if (course.getMpLeaderCreateButton() != null && matchesButton(course.getMpLeaderCreateButton(), worldName, blockX, blockY, blockZ)) {
                clickedCourse = course;
                buttonType = "mp_leader_create";
                break;
            }
            if (course.getMpLeaderStartButton() != null && matchesButton(course.getMpLeaderStartButton(), worldName, blockX, blockY, blockZ)) {
                clickedCourse = course;
                buttonType = "mp_leader_start";
                break;
            }
            if (course.getMpLeaderCancelButton() != null && matchesButton(course.getMpLeaderCancelButton(), worldName, blockX, blockY, blockZ)) {
                clickedCourse = course;
                buttonType = "mp_leader_cancel";
                break;
            }
        }
        
        if (clickedCourse == null || buttonType == null) {
            return; // Not a course button
        }
        
        // Cancel event to prevent block interaction
        event.setCancelled(true);
        
        // Route to appropriate handler
        switch (buttonType) {
            case "solo_join":
                handleSoloJoin(player, clickedCourse);
                break;
            case "solo_return":
                handleSoloReturn(player, clickedCourse);
                break;
            case "mp_join":
                handleMpJoin(player, clickedCourse);
                break;
            case "mp_leader_create":
                handleMpLeaderCreate(player, clickedCourse);
                break;
            case "mp_leader_start":
                handleMpLeaderStart(player, clickedCourse);
                break;
            case "mp_leader_cancel":
                handleMpLeaderCancel(player, clickedCourse);
                break;
        }
    }
    
    private boolean matchesButton(Course.BlockCoord button, String worldName, int x, int y, int z) {
        return button.getWorld().equals(worldName) &&
               button.getX() == x &&
               button.getY() == y &&
               button.getZ() == z;
    }
    
    private boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("bocrace.admin");
    }
    
    private void sendNotReadyMessage(Player player, Course course) {
        String msg = "§cCourse not ready yet.";
        if (isAdmin(player)) {
            msg += " Run /bocrace status " + course.getName();
        }
        player.sendMessage(msg);
    }
    
    private void handleSoloJoin(Player player, Course course) {
        // CRITICAL: Validate course is complete before allowing gameplay
        CourseValidator.ValidationResult validation = CourseValidator.validate(course);
        if (!validation.isOk()) {
            player.sendMessage("§cCourse is not ready for racing!");
            if (player.isOp() || player.hasPermission("bocrace.admin")) {
                player.sendMessage("§7Issues: " + String.join(", ", validation.getIssues()));
                player.sendMessage("§7Run /bocrace status " + course.getName() + " to see details");
            }
            return;
        }
        
        // Readiness check: exactly 1 spawn AND soloJoinButton exists
        int spawnCount = course.getPlayerSpawns().size();
        if (spawnCount != 1 || course.getSoloJoinButton() == null) {
            sendNotReadyMessage(player, course);
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        
        // Check if course is in use (lock OR active run)
        RaceManager.SoloLock lock = raceManager.getSoloLock(key);
        Map<UUID, RaceManager.ActiveRun> activeRuns = raceManager.getActiveRuns(key);
        boolean hasActiveRun = !activeRuns.isEmpty();
        
        if (lock != null && !lock.isExpired()) {
            long remaining = lock.getRemainingSeconds();
            player.sendMessage("§cCourse in use. Try again in " + remaining + " seconds.");
            // Debug log
            Map<String, Object> kv = new HashMap<>();
            kv.put("course", course.getName());
            kv.put("player", player.getName());
            kv.put("remaining", remaining);
            plugin.getDebugLog().info(DebugLog.Tag.RULE, "CourseButtonListener", "SOLO join blocked by lock", kv);
            return;
        }
        
        if (hasActiveRun) {
            player.sendMessage("§cSomeone is currently racing this course. Please wait for them to finish.");
            // Debug log
            Map<String, Object> kv = new HashMap<>();
            kv.put("course", course.getName());
            kv.put("player", player.getName());
            plugin.getDebugLog().info(DebugLog.Tag.RULE, "CourseButtonListener", "SOLO join blocked by active run", kv);
            return;
        }
        
        // Acquire lock using course settings
        int cooldownSeconds = course.getSettings().getSoloCooldownSeconds();
        raceManager.acquireSoloLock(key, player.getUniqueId(), cooldownSeconds);
        
        // Debug log
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", course.getName());
        kv.put("player", player.getName());
        kv.put("lockSeconds", cooldownSeconds);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "SOLO join success", kv);
        
        // Get settings before creating run
        int countdownSeconds = course.getSettings().getCountdownSeconds();
        Course.StartMode startMode = course.getSettings().getStartMode();
        
        // Create active run
        RaceManager.ActiveRun run = raceManager.createActiveRun(key, player.getUniqueId(), 0);
        
        // Database: Create run record (async)
        if (plugin.getRunDao() != null && plugin.getPlayerDao() != null) {
            plugin.getPlayerDao().upsertPlayer(player.getUniqueId(), player.getName());
            String courseFile = courseManager.getCourseFileName(course.getType(), course.getName());
            plugin.getRunDao().createRun(run.getRunId(), course.getName(), course.getType().name(), courseFile,
                                        player.getUniqueId(), startMode, 
                                        course.getSettings().getRules().isRequireCheckpoints(),
                                        course.getSettings().getDrop().getShape());
        }
        
        // Debug log run creation
        Map<String, Object> runKv = new HashMap<>();
        runKv.put("course", course.getName());
        runKv.put("player", player.getName());
        runKv.put("spawnIndex", 0);
        runKv.put("startMode", startMode.name());
        runKv.put("runId", run.getRunId());
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "RUN_CREATE (SOLO)", runKv);
        
        // Get spawn location (used for both boat and air)
        Location spawn = course.getPlayerSpawns().get(0);
        
        // IMMEDIATELY teleport player to spawn and place in boat (if BOAT course)
        if (course.getType() == com.bocrace.model.CourseType.BOAT) {
            // Teleport player first
            player.teleport(spawn);
            // Then spawn boat and place player in it
            org.bukkit.entity.Boat boat = plugin.getBoatManager().spawnRaceBoat(player, spawn, course, run.getRunId());
            if (boat == null) {
                player.sendMessage("§cFailed to spawn boat! Please contact an admin.");
                raceManager.removeActiveRun(key, player.getUniqueId());
                raceManager.releaseSoloLock(key, player.getUniqueId());
                return;
            }
        } else {
            // AIR courses: just teleport to spawn
            player.teleport(spawn);
        }
        
        // Debug log countdown start
        Map<String, Object> countdownKv = new HashMap<>();
        countdownKv.put("course", course.getName());
        countdownKv.put("player", player.getName());
        countdownKv.put("mode", startMode.name());
        countdownKv.put("countdownSeconds", countdownSeconds);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "SOLO countdown start", countdownKv);
        
        // Start countdown (player is already in position)
        new BukkitRunnable() {
            int countdown = countdownSeconds;
            
            @Override
            public void run() {
                if (countdown > 0) {
                    player.sendMessage("§6" + countdown + "...");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    countdown--;
                } else {
                    // GO!
                    player.sendMessage("§a§lGO!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    
                    if (startMode == Course.StartMode.CROSS_LINE) {
                        // Don't start timer yet - wait for start line crossing
                        player.sendMessage("§7Cross the start line to begin!");
                    } else if (startMode == Course.StartMode.DROP_START) {
                        // Start timer immediately
                        long startMillis = System.currentTimeMillis();
                        run.setStartMillis(startMillis);
                        
                        // Database: Mark run as started (async)
                        if (plugin.getRunDao() != null) {
                            plugin.getRunDao().markStarted(run.getRunId(), startMillis, course.getName(), player.getUniqueId());
                        }
                        
                        // Debug log (run start for DROP_START)
                        Map<String, Object> startKv = new HashMap<>();
                        startKv.put("course", course.getName());
                        startKv.put("player", player.getName());
                        startKv.put("via", "DROP_GO");
                        plugin.getDebugLog().info(DebugLog.Tag.DETECT, "CourseButtonListener", "RUN_START (SOLO)", startKv);
                        
                        // Drop blocks (only for boat courses, at spawn location)
                        if (course.getType() == com.bocrace.model.CourseType.BOAT) {
                            dropBlockManager.dropBlocks(key, spawn, course.getSettings().getDrop());
                        }
                    }
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 second
    }
    
    private void handleSoloReturn(Player player, Course course) {
        // Readiness check: soloReturnButton exists AND courseLobby exists
        if (course.getSoloReturnButton() == null || course.getCourseLobbySpawn() == null) {
            sendNotReadyMessage(player, course);
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        
        // Clear lock only if this player holds it
        if (raceManager.releaseSoloLock(key, player.getUniqueId())) {
            // Clear active run
            raceManager.removeActiveRun(key, player.getUniqueId());
            // Cancel any pending block drops
            dropBlockManager.cancelAllDrops(key);
            
            // Remove boat if player is in one
            if (course.getType() == com.bocrace.model.CourseType.BOAT) {
                org.bukkit.entity.Boat boat = plugin.getBoatManager().findRaceBoatByPlayer(player.getUniqueId());
                if (boat != null) {
                    plugin.getBoatManager().removeRaceBoat(boat, "player_returned");
                }
            }
            
            player.teleport(course.getCourseLobbySpawn());
            player.sendMessage("§aReturned to course lobby.");
            
            // Debug log
            Map<String, Object> kv = new HashMap<>();
            kv.put("course", course.getName());
            kv.put("player", player.getName());
            plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "SOLO return", kv);
        } else {
            player.sendMessage("§cYou don't have an active solo run.");
        }
    }
    
    private void handleMpJoin(Player player, Course course) {
        // Readiness check: mpLobby exists AND mpJoinButton exists AND playerSpawns >= 2
        int spawnCount = course.getPlayerSpawns().size();
        if (course.getMpLobby() == null || course.getMpJoinButton() == null || spawnCount < 2) {
            sendNotReadyMessage(player, course);
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState existingLobby = raceManager.getMultiLobby(key);
        RaceManager.MultiLobbyState lobby = raceManager.getOrCreateMultiLobby(key);
        
        // Debug log lobby creation (only if it was just created)
        if (existingLobby == null) {
            Map<String, Object> lobbyKv = new HashMap<>();
            lobbyKv.put("course", course.getName());
            lobbyKv.put("leader", lobby.getLeaderUuid() != null ? Bukkit.getOfflinePlayer(lobby.getLeaderUuid()).getName() : "none");
            plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "LOBBY_CREATE", lobbyKv);
        }
        
        // Prevent join if race already started
        if (lobby.getState() == RaceManager.MultiLobbyState.LobbyState.IN_PROGRESS) {
            player.sendMessage("§cRace already started.");
            return;
        }
        
        // Check if already joined
        if (lobby.getJoinedPlayers().containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou're already in the lobby.");
            return;
        }
        
        // If no leader exists yet, first joining player becomes leader
        if (lobby.getLeaderUuid() == null) {
            lobby.setLeaderUuid(player.getUniqueId());
        }
        
        // Find available spawn index
        Set<Integer> used = lobby.getUsedSpawnIndices();
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < spawnCount; i++) {
            if (!used.contains(i)) {
                available.add(i);
            }
        }
        
        if (available.isEmpty()) {
            player.sendMessage("§cRace lobby full.");
            return;
        }
        
        // Random selection
        Random random = new Random();
        int spawnIndex = available.get(random.nextInt(available.size()));
        
        // Assign spawn
        lobby.getJoinedPlayers().put(player.getUniqueId(), spawnIndex);
        lobby.getUsedSpawnIndices().add(spawnIndex);
        
        // Teleport immediately
        Location spawn = course.getPlayerSpawns().get(spawnIndex);
        player.teleport(spawn);
        
        player.sendMessage("§aJoined " + course.getName() + ". Waiting for leader...");
        
        // Optional broadcast
        int totalPlayers = lobby.getJoinedPlayers().size();
        for (UUID uuid : lobby.getJoinedPlayers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !p.equals(player)) {
                p.sendMessage("§7" + player.getName() + " joined (" + totalPlayers + "/" + spawnCount + ").");
            }
        }
        
        // Debug log
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", course.getName());
        kv.put("player", player.getName());
        kv.put("spawnIndex", spawnIndex);
        kv.put("leader", lobby.getLeaderUuid() != null && lobby.getLeaderUuid().equals(player.getUniqueId()));
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "MP join", kv);
    }
    
    private void handleMpLeaderCreate(Player player, Course course) {
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getOrCreateMultiLobby(key);
        
        lobby.setLeaderUuid(player.getUniqueId());
        
        Bukkit.broadcastMessage("§6Race forming: " + course.getName() + ". Click join to enter.");
    }
    
    private void handleMpLeaderStart(Player player, Course course) {
        // Readiness check: mpLeaderStartButton exists AND playerSpawns >= 2
        int spawnCount = course.getPlayerSpawns().size();
        if (course.getMpLeaderStartButton() == null || spawnCount < 2) {
            sendNotReadyMessage(player, course);
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getOrCreateMultiLobby(key);
        
        // Prevent start if not OPEN (already STARTING or IN_PROGRESS)
        if (lobby.getState() != RaceManager.MultiLobbyState.LobbyState.OPEN) {
            player.sendMessage("§cRace already starting or in progress.");
            return;
        }
        
        // Check if leader
        if (lobby.getLeaderUuid() == null || !lobby.getLeaderUuid().equals(player.getUniqueId())) {
            player.sendMessage("§cOnly the race leader can start the race.");
            return;
        }
        
        // Count total players (including leader if not yet assigned)
        int totalPlayers = lobby.getJoinedPlayers().size();
        if (!lobby.getJoinedPlayers().containsKey(player.getUniqueId())) {
            totalPlayers++; // Leader not yet counted
        }
        
        if (totalPlayers < 2) {
            player.sendMessage("§cNeed at least 2 racers to start.");
            return;
        }
        
        // Assign leader spawn if not yet assigned
        if (!lobby.getJoinedPlayers().containsKey(player.getUniqueId())) {
            Set<Integer> used = lobby.getUsedSpawnIndices();
            List<Integer> available = new ArrayList<>();
            for (int i = 0; i < spawnCount; i++) {
                if (!used.contains(i)) {
                    available.add(i);
                }
            }
            if (!available.isEmpty()) {
                Random random = new Random();
                int spawnIndex = available.get(random.nextInt(available.size()));
                lobby.getJoinedPlayers().put(player.getUniqueId(), spawnIndex);
                lobby.getUsedSpawnIndices().add(spawnIndex);
                
                // Teleport leader immediately
                Location spawn = course.getPlayerSpawns().get(spawnIndex);
                player.teleport(spawn);
            }
        }
        
        // Set state to STARTING
        lobby.setState(RaceManager.MultiLobbyState.LobbyState.STARTING);
        
        // Debug log start pressed
        Map<String, Object> startKv = new HashMap<>();
        startKv.put("course", course.getName());
        startKv.put("player", player.getName());
        startKv.put("isLeader", true);
        startKv.put("joined", lobby.getJoinedPlayers().size());
        startKv.put("spawns", spawnCount);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "MP start pressed", startKv);
        
        // Get settings before creating runs
        int countdownSeconds = course.getSettings().getCountdownSeconds();
        Course.StartMode startMode = course.getSettings().getStartMode();
        
        // Get all players (including leader if not in joinedPlayers)
        Set<UUID> allRacers = new HashSet<>(lobby.getJoinedPlayers().keySet());
        
        // Create active runs for all racers
        Map<UUID, RaceManager.ActiveRun> runs = new HashMap<>();
        for (UUID uuid : allRacers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Integer spawnIdx = lobby.getJoinedPlayers().get(uuid);
                if (spawnIdx != null) {
                    RaceManager.ActiveRun run = raceManager.createActiveRun(key, uuid, spawnIdx);
                    runs.put(uuid, run);
                    
                    // Database: Create run record (async)
                    if (plugin.getRunDao() != null && plugin.getPlayerDao() != null) {
                        plugin.getPlayerDao().upsertPlayer(uuid, p.getName());
                        String courseFile = courseManager.getCourseFileName(course.getType(), course.getName());
                        plugin.getRunDao().createRun(run.getRunId(), course.getName(), course.getType().name(), courseFile,
                                                    uuid, startMode,
                                                    course.getSettings().getRules().isRequireCheckpoints(),
                                                    course.getSettings().getDrop().getShape());
                    }
                    
                    // Debug log run creation
                    Map<String, Object> runKv = new HashMap<>();
                    runKv.put("course", course.getName());
                    runKv.put("player", p.getName());
                    runKv.put("spawnIndex", spawnIdx);
                    runKv.put("startMode", startMode.name());
                    runKv.put("runId", run.getRunId());
                    plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "RUN_CREATE (MP)", runKv);
                }
            }
        }
        
        // Countdown
        
        // Debug log countdown start
        Map<String, Object> countdownKv = new HashMap<>();
        countdownKv.put("course", course.getName());
        countdownKv.put("mode", startMode.name());
        countdownKv.put("countdownSeconds", countdownSeconds);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "MP countdown start", countdownKv);
        
        new BukkitRunnable() {
            int countdown = countdownSeconds;
            
            @Override
            public void run() {
                if (countdown > 0) {
                    // Send to all players in lobby
                    String message = "§6" + countdown + "...";
                    for (UUID uuid : allRacers) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(message);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        }
                    }
                    countdown--;
                } else {
                    // GO - Spawn boats and start race
                    lobby.setState(RaceManager.MultiLobbyState.LobbyState.IN_PROGRESS);
                    String goMessage = "§a§lGO!";
                    
                    for (UUID uuid : allRacers) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null) continue;
                        
                        RaceManager.ActiveRun run = runs.get(uuid);
                        if (run == null) continue;
                        
                        Integer spawnIdx = lobby.getJoinedPlayers().get(uuid);
                        if (spawnIdx == null) continue;
                        
                        Location spawn = course.getPlayerSpawns().get(spawnIdx);
                        
                        // Spawn boat for BOAT courses
                        if (course.getType() == com.bocrace.model.CourseType.BOAT) {
                            org.bukkit.entity.Boat boat = plugin.getBoatManager().spawnRaceBoat(p, spawn, course, run.getRunId());
                            if (boat == null) {
                                p.sendMessage("§cFailed to spawn boat! Please contact an admin.");
                                raceManager.removeActiveRun(key, uuid);
                                continue;
                            }
                        } else {
                            // AIR courses: just teleport to spawn
                            p.teleport(spawn);
                        }
                        
                        p.sendMessage(goMessage);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        
                        if (startMode == Course.StartMode.CROSS_LINE) {
                            // Don't start timer yet
                            p.sendMessage("§7Cross the start line to begin!");
                        } else if (startMode == Course.StartMode.DROP_START) {
                            // Start timer immediately
                            long startMillis = System.currentTimeMillis();
                            run.setStartMillis(startMillis);
                            
                            // Database: Mark run as started (async)
                            if (plugin.getRunDao() != null) {
                                plugin.getRunDao().markStarted(run.getRunId(), startMillis, course.getName(), uuid);
                            }
                            
                            // Debug log (run start for DROP_START)
                            Map<String, Object> startKv = new HashMap<>();
                            startKv.put("course", course.getName());
                            startKv.put("player", p.getName());
                            startKv.put("via", "DROP_GO");
                            plugin.getDebugLog().info(DebugLog.Tag.DETECT, "CourseButtonListener", "RUN_START (MP)", startKv);
                            
                            // Drop blocks under spawn (only for BOAT courses)
                            if (course.getType() == com.bocrace.model.CourseType.BOAT) {
                                dropBlockManager.dropBlocks(key, spawn, course.getSettings().getDrop());
                            }
                        }
                    }
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 second
    }
    
    private void handleMpLeaderCancel(Player player, Course course) {
        // Readiness check: mpLeaderCancelButton exists
        if (course.getMpLeaderCancelButton() == null) {
            sendNotReadyMessage(player, course);
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getMultiLobby(key);
        
        if (lobby == null) {
            player.sendMessage("§cNo active lobby for this course.");
            return;
        }
        
        // Check if leader (OP override allowed)
        boolean isLeader = lobby.getLeaderUuid() != null && lobby.getLeaderUuid().equals(player.getUniqueId());
        boolean opOverride = !isLeader && isAdmin(player);
        if (!isLeader && !opOverride) {
            player.sendMessage("§cOnly the race leader can cancel the race.");
            return;
        }
        
        // Determine return location
        Location returnLoc = course.getMpLobby();
        if (returnLoc == null) {
            returnLoc = course.getCourseLobbySpawn();
        }
        
        if (returnLoc == null) {
            player.sendMessage("§cNo return location set for this course.");
            return;
        }
        
        // Cancel any pending block drops
        dropBlockManager.cancelAllDrops(key);
        
        // Database: Abort all runs in lobby (async)
        Map<UUID, RaceManager.ActiveRun> runs = raceManager.getActiveRuns(key);
        if (plugin.getRunDao() != null) {
            for (RaceManager.ActiveRun run : runs.values()) {
                plugin.getRunDao().abortRun(run.getRunId(), "Leader cancelled race", course.getName(), run.getRacerUuid());
            }
        }
        
        // Remove boats for all racers (for BOAT courses)
        if (course.getType() == com.bocrace.model.CourseType.BOAT) {
            for (RaceManager.ActiveRun run : runs.values()) {
                org.bukkit.entity.Boat boat = plugin.getBoatManager().findRaceBoatByPlayer(run.getRacerUuid());
                if (boat != null) {
                    plugin.getBoatManager().removeRaceBoat(boat, "race_cancelled");
                }
            }
        }
        
        // Clear active runs and lobby
        raceManager.clearActiveRuns(key);
        raceManager.clearMultiLobby(key);
        
        // Debug log
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", course.getName());
        kv.put("player", player.getName());
        kv.put("isLeader", isLeader);
        kv.put("opOverride", opOverride);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "MP cancel", kv);
        kv.put("reason", "cancel");
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "RUN_CLEAR (MP cancel)", kv);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "CourseButtonListener", "LOBBY_CLEAR (MP cancel)", kv);
        
        // Teleport all players (including leader)
        Set<UUID> allPlayers = new HashSet<>(lobby.getJoinedPlayers().keySet());
        if (lobby.getLeaderUuid() != null) {
            allPlayers.add(lobby.getLeaderUuid());
        }
        
        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(returnLoc);
                p.sendMessage("§cRace cancelled.");
            }
        }
    }
}
