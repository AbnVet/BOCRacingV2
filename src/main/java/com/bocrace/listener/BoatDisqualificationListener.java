package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.model.CourseType;
import com.bocrace.runtime.RaceManager;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.BoatManager;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles boat exit and destruction events to disqualify players
 */
public class BoatDisqualificationListener implements Listener {
    
    private final BOCRacingV2 plugin;
    private final RaceManager raceManager;
    private final CourseManager courseManager;
    private final BoatManager boatManager;
    
    public BoatDisqualificationListener(BOCRacingV2 plugin, RaceManager raceManager, CourseManager courseManager, BoatManager boatManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.courseManager = courseManager;
        this.boatManager = boatManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Boat)) {
            return;
        }
        
        Boat boat = (Boat) event.getVehicle();
        if (!boatManager.isRaceBoat(boat)) {
            return;
        }
        
        if (!(event.getExited() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getExited();
        UUID playerUuid = player.getUniqueId();
        
        // Find the active run for this player
        RaceManager.ActiveRun run = raceManager.getActiveRun(playerUuid);
        if (run == null) {
            return;
        }
        
        // Only DQ if the run has started (timer is running)
        if (!run.isStarted() || run.isFinished()) {
            return;
        }
        
        // Check if this is a BOAT course
        RaceManager.CourseKey courseKey = run.getCourseKey();
        Course course = courseManager.findCourse(courseKey.getName());
        if (course == null || course.getType() != CourseType.BOAT) {
            return;
        }
        
        // Disqualify the player
        disqualifyPlayer(player, run, courseKey, "Exited boat");
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBoatDestroyed(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Boat)) {
            return;
        }
        
        Boat boat = (Boat) entity;
        if (!boatManager.isRaceBoat(boat)) {
            return;
        }
        
        // Find the player who owns this boat
        UUID playerUuid = boatManager.getRaceBoatPlayer(boat);
        if (playerUuid == null) {
            return;
        }
        
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // Find the active run for this player
        RaceManager.ActiveRun run = raceManager.getActiveRun(playerUuid);
        if (run == null) {
            return;
        }
        
        // Only DQ if the run has started (timer is running)
        if (!run.isStarted() || run.isFinished()) {
            return;
        }
        
        // Check if this is a BOAT course
        RaceManager.CourseKey courseKey = run.getCourseKey();
        Course course = courseManager.findCourse(courseKey.getName());
        if (course == null || course.getType() != CourseType.BOAT) {
            return;
        }
        
        // Disqualify the player
        disqualifyPlayer(player, run, courseKey, "Boat destroyed");
    }
    
    /**
     * Disqualify a player from their race
     */
    private void disqualifyPlayer(Player player, RaceManager.ActiveRun run, RaceManager.CourseKey courseKey, String reason) {
        UUID playerUuid = player.getUniqueId();
        
        // Mark run as finished (DQ)
        long dqMillis = System.currentTimeMillis();
        run.setFinishMillis(dqMillis);
        long elapsedMillis = run.getElapsedMillis();
        
        // Notify player
        player.sendMessage("§c§l❌ DISQUALIFIED! §c" + reason);
        String timeStr = formatTime(elapsedMillis);
        player.sendMessage("§7Time at DQ: §f" + timeStr);
        
        // Database: Record DQ
        if (plugin.getRunDao() != null) {
            plugin.getRunDao().dqRun(run.getRunId(), reason, courseKey.getName(), playerUuid);
        }
        
        // Remove boat
        Boat boat = boatManager.findRaceBoatByPlayer(playerUuid);
        if (boat != null) {
            boatManager.removeRaceBoat(boat, "disqualified");
        }
        
        // Teleport to course lobby if available
        Course course = courseManager.findCourse(courseKey.getName());
        if (course != null && course.getCourseLobbySpawn() != null) {
            if (course.getCourseLobbySpawn().getWorld() != null) {
                player.teleport(course.getCourseLobbySpawn());
                player.sendMessage("§7Teleported to course lobby.");
            }
        }
        
        // Cleanup race state
        Map<UUID, RaceManager.ActiveRun> courseRuns = raceManager.getActiveRuns(courseKey);
        if (courseRuns.size() == 1) {
            // Solo run DQ - clear lock and run
            raceManager.releaseSoloLock(courseKey, playerUuid);
            raceManager.removeActiveRun(courseKey, playerUuid);
        } else {
            // MP run DQ - remove from active runs first
            raceManager.removeActiveRun(courseKey, playerUuid);
            
            // Get updated runs after removal
            Map<UUID, RaceManager.ActiveRun> remainingRuns = raceManager.getActiveRuns(courseKey);
            
            // Check if all remaining racers are done
            boolean allDone = true;
            if (!remainingRuns.isEmpty()) {
                for (RaceManager.ActiveRun r : remainingRuns.values()) {
                    if (!r.isFinished()) {
                        allDone = false;
                        break;
                    }
                }
            } else {
                allDone = true; // No runs left
            }
            
            if (allDone) {
                raceManager.clearActiveRuns(courseKey);
                RaceManager.MultiLobbyState lobby = raceManager.getMultiLobby(courseKey);
                if (lobby != null && lobby.getState() == RaceManager.MultiLobbyState.LobbyState.IN_PROGRESS) {
                    raceManager.clearMultiLobby(courseKey);
                }
            }
        }
        
        // Debug log
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", courseKey.getName());
        kv.put("player", player.getName());
        kv.put("reason", reason);
        kv.put("timeMs", elapsedMillis);
        plugin.getDebugLog().info(com.bocrace.util.DebugLog.Tag.RULE, "BoatDisqualification", "RUN_DQ", kv);
    }
    
    /**
     * Format milliseconds as mm:ss.SSS
     */
    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
}
