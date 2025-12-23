package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.model.CourseType;
import com.bocrace.runtime.RaceManager;
import com.bocrace.storage.CourseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    
    public CourseButtonListener(BOCRacingV2 plugin, CourseManager courseManager, RaceManager raceManager) {
        this.plugin = plugin;
        this.courseManager = courseManager;
        this.raceManager = raceManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
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
    
    private void handleSoloJoin(Player player, Course course) {
        int spawnCount = course.getPlayerSpawns().size();
        if (spawnCount != 1) {
            player.sendMessage("§cSolo course misconfigured: needs exactly 1 spawn.");
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.SoloLock lock = raceManager.getSoloLock(key);
        
        if (lock != null && !lock.isExpired()) {
            long remaining = lock.getRemainingSeconds();
            player.sendMessage("§cCourse in use. Try again in " + remaining + " seconds.");
            return;
        }
        
        // Acquire lock for 120 seconds
        raceManager.acquireSoloLock(key, player.getUniqueId(), 120);
        
        // Teleport to solo spawn
        Location spawn = course.getPlayerSpawns().get(0);
        player.teleport(spawn);
        
        player.sendMessage("§aSolo run started (stub).");
    }
    
    private void handleSoloReturn(Player player, Course course) {
        if (course.getCourseLobbySpawn() == null) {
            player.sendMessage("§cCourse lobby not set.");
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        
        // Clear lock only if this player holds it
        if (raceManager.releaseSoloLock(key, player.getUniqueId())) {
            player.teleport(course.getCourseLobbySpawn());
            player.sendMessage("§aReturned to course lobby.");
        } else {
            player.sendMessage("§cYou don't have an active solo run.");
        }
    }
    
    private void handleMpJoin(Player player, Course course) {
        int spawnCount = course.getPlayerSpawns().size();
        if (spawnCount < 2) {
            player.sendMessage("§cMultiplayer misconfigured: need 2+ player spawns.");
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getOrCreateMultiLobby(key);
        
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
    }
    
    private void handleMpLeaderCreate(Player player, Course course) {
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getOrCreateMultiLobby(key);
        
        lobby.setLeaderUuid(player.getUniqueId());
        
        Bukkit.broadcastMessage("§6Race forming: " + course.getName() + ". Click join to enter.");
    }
    
    private void handleMpLeaderStart(Player player, Course course) {
        int spawnCount = course.getPlayerSpawns().size();
        if (spawnCount < 2) {
            player.sendMessage("§cMultiplayer misconfigured: need 2+ player spawns.");
            return;
        }
        
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getOrCreateMultiLobby(key);
        
        // Prevent start if not OPEN (already STARTING or IN_PROGRESS)
        if (lobby.getState() != RaceManager.MultiLobbyState.LobbyState.OPEN) {
            player.sendMessage("§cRace already starting or in progress.");
            return;
        }
        
        // Set leader if not set
        if (lobby.getLeaderUuid() == null) {
            lobby.setLeaderUuid(player.getUniqueId());
        }
        
        // Check if leader
        if (!lobby.getLeaderUuid().equals(player.getUniqueId())) {
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
        
        // Countdown
        new BukkitRunnable() {
            int countdown = 5;
            
            @Override
            public void run() {
                if (countdown > 0) {
                    // Send to all players in lobby
                    String message = "§6" + countdown + "...";
                    for (UUID uuid : lobby.getJoinedPlayers().keySet()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(message);
                        }
                    }
                    countdown--;
                } else {
                    // GO
                    lobby.setState(RaceManager.MultiLobbyState.LobbyState.IN_PROGRESS);
                    String message = "§a§lGO!";
                    for (UUID uuid : lobby.getJoinedPlayers().keySet()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(message);
                        }
                    }
                    
                    // Stub message
                    for (UUID uuid : lobby.getJoinedPlayers().keySet()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage("§aRace started (stub).");
                        }
                    }
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20 ticks = 1 second
    }
    
    private void handleMpLeaderCancel(Player player, Course course) {
        RaceManager.CourseKey key = new RaceManager.CourseKey(course.getType().name(), course.getName());
        RaceManager.MultiLobbyState lobby = raceManager.getMultiLobby(key);
        
        if (lobby == null) {
            player.sendMessage("§cNo active lobby for this course.");
            return;
        }
        
        // Check if leader
        if (lobby.getLeaderUuid() == null || !lobby.getLeaderUuid().equals(player.getUniqueId())) {
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
        
        // Clear lobby
        raceManager.clearMultiLobby(key);
    }
}
