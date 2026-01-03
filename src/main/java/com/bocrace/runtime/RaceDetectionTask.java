package com.bocrace.runtime;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.model.Course.VolumeRegion;
import com.bocrace.model.Course.CheckpointRegion;
import com.bocrace.model.Course.BlockCoord;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.DebugLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repeating task to detect start/finish line crossings
 */
public class RaceDetectionTask extends BukkitRunnable {
    
    private final BOCRacingV2 plugin;
    private final RaceManager raceManager;
    private final CourseManager courseManager;
    
    public RaceDetectionTask(BOCRacingV2 plugin, RaceManager raceManager, CourseManager courseManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.courseManager = courseManager;
    }
    
    @Override
    public void run() {
        // Iterate all active runs (create copy to avoid concurrent modification)
        Map<RaceManager.CourseKey, Map<UUID, RaceManager.ActiveRun>> runsMap = raceManager.getActiveRunsMap();
        for (Map.Entry<RaceManager.CourseKey, Map<UUID, RaceManager.ActiveRun>> entry : runsMap.entrySet()) {
            RaceManager.CourseKey courseKey = entry.getKey();
            Map<UUID, RaceManager.ActiveRun> runs = entry.getValue();
            
            Course course = courseManager.findCourse(courseKey.getName());
            if (course == null) continue;
            
            Course.StartMode startMode = course.getSettings().getStartMode();
            VolumeRegion startRegion = course.getStartRegion();
            VolumeRegion finishRegion = course.getFinishRegion();
            boolean requireCheckpoints = course.getSettings().getRules().isRequireCheckpoints();
            List<CheckpointRegion> checkpoints = course.getCheckpoints();
            int totalCheckpoints = checkpoints != null ? checkpoints.size() : 0;
            
            for (RaceManager.ActiveRun run : runs.values()) {
                Player player = Bukkit.getPlayer(run.getRacerUuid());
                if (player == null || !player.isOnline()) continue;
                
                Location playerLoc = player.getLocation();
                
                // Check start (only for CROSS_LINE mode)
                if (startMode == Course.StartMode.CROSS_LINE && !run.isStarted() && startRegion != null) {
                    if (isInRegion(playerLoc, startRegion)) {
                        // Start the timer
                        long startMillis = System.currentTimeMillis();
                        run.setStartMillis(startMillis);
                        player.sendMessage("§aTimer started!");
                        
                        // Database: Mark run as started (async)
                        if (plugin.getRunDao() != null) {
                            plugin.getRunDao().markStarted(run.getRunId(), startMillis, courseKey.getName(), run.getRacerUuid());
                        }
                        
                        // Debug log
                        Map<String, Object> kv = new HashMap<>();
                        kv.put("course", courseKey.getName());
                        kv.put("player", player.getName());
                        kv.put("via", "CROSS_LINE");
                        plugin.getDebugLog().info(DebugLog.Tag.DETECT, "RaceDetection", "RUN_START", kv);
                    }
                }
                
                // Check checkpoints (only if required and run is started)
                if (run.isStarted() && !run.isFinished() && requireCheckpoints && totalCheckpoints > 0) {
                    checkCheckpoints(player, run, checkpoints, playerLoc);
                }
                
                // Update HUD (elapsed time + checkpoint progress)
                if (run.isStarted() && !run.isFinished()) {
                    updateHUD(player, run, requireCheckpoints, totalCheckpoints);
                }
                
                // Check finish (for all modes)
                if (run.isStarted() && !run.isFinished() && finishRegion != null) {
                    if (isInRegion(playerLoc, finishRegion)) {
                        // Check if all required checkpoints are passed
                        if (requireCheckpoints && totalCheckpoints > 0) {
                            if (run.getNextRequiredCheckpointIndex() > totalCheckpoints) {
                                // All checkpoints passed, allow finish
                                finishRace(player, run, courseKey);
                            } else {
                                // Missing checkpoints
                                int missing = run.getNextRequiredCheckpointIndex();
                                player.sendMessage("§cMissing checkpoint #" + missing);
                                
                                // Debug log
                                Map<String, Object> kv = new HashMap<>();
                                kv.put("course", courseKey.getName());
                                kv.put("player", player.getName());
                                kv.put("missing", missing);
                                kv.put("total", totalCheckpoints);
                                plugin.getDebugLog().info(DebugLog.Tag.RULE, "RaceDetection", "Finish blocked (missing checkpoint)", kv);
                            }
                        } else {
                            // No checkpoints required, allow finish
                            finishRace(player, run, courseKey);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Check if a location is inside a volume region
     */
    private boolean isInRegion(Location loc, VolumeRegion region) {
        World world = loc.getWorld();
        if (world == null || !world.getName().equals(region.getWorld())) {
            return false;
        }
        
        if (region.getMin() == null || region.getMax() == null) {
            return false;
        }
        
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        int minX = Math.min(region.getMin().getX(), region.getMax().getX());
        int maxX = Math.max(region.getMin().getX(), region.getMax().getX());
        int minY = Math.min(region.getMin().getY(), region.getMax().getY());
        int maxY = Math.max(region.getMin().getY(), region.getMax().getY());
        int minZ = Math.min(region.getMin().getZ(), region.getMax().getZ());
        int maxZ = Math.max(region.getMin().getZ(), region.getMax().getZ());
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Check if a location is inside a checkpoint region (cuboid between point1 and point2)
     */
    private boolean isInCheckpoint(Location loc, CheckpointRegion checkpoint) {
        BlockCoord p1 = checkpoint.getPoint1();
        BlockCoord p2 = checkpoint.getPoint2();
        if (p1 == null || p2 == null) return false;
        
        World world = loc.getWorld();
        if (world == null || !world.getName().equals(p1.getWorld())) {
            return false;
        }
        
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Check checkpoints for a racer
     */
    private void checkCheckpoints(Player player, RaceManager.ActiveRun run, 
                                   List<CheckpointRegion> checkpoints, Location playerLoc) {
        int nextRequired = run.getNextRequiredCheckpointIndex();
        
        // Find checkpoint with matching index
        CheckpointRegion targetCheckpoint = null;
        for (CheckpointRegion cp : checkpoints) {
            if (cp.getCheckpointIndex() == nextRequired) {
                targetCheckpoint = cp;
                break;
            }
        }
        
        if (targetCheckpoint == null) {
            // Next required checkpoint doesn't exist (shouldn't happen if validation worked)
            return;
        }
        
        // Check if player is in the correct checkpoint
        if (isInCheckpoint(playerLoc, targetCheckpoint)) {
            // Mark checkpoint as passed
            run.getPassedCheckpoints().add(nextRequired);
            long splitTime = System.currentTimeMillis() - run.getStartMillis();
            run.getCheckpointSplitTimes().put(nextRequired, splitTime);
            
            // Advance to next checkpoint
            run.setNextRequiredCheckpointIndex(nextRequired + 1);
            
            // Database: Record checkpoint split (async)
            if (plugin.getRunDao() != null) {
                plugin.getRunDao().recordCheckpoint(run.getRunId(), nextRequired, splitTime, run.getCourseKey().getName(), run.getRacerUuid());
            }
            
            // Debug log
            Map<String, Object> kv = new HashMap<>();
            kv.put("course", run.getCourseKey().getName());
            kv.put("player", player.getName());
            kv.put("checkpointIndex", nextRequired);
            kv.put("splitTimeMs", splitTime);
            plugin.getDebugLog().info(DebugLog.Tag.DETECT, "RaceDetection", "Checkpoint passed", kv);
            
            // Send message (with cooldown to avoid spam)
            long now = System.currentTimeMillis();
            if (now - run.getLastCheckpointMessageMillis() >= 1000) { // 1 second cooldown
                int total = checkpoints.size();
                Component message = Component.text()
                    .append(Component.text("Checkpoint ", NamedTextColor.GREEN))
                    .append(Component.text(nextRequired + "/" + total, NamedTextColor.YELLOW))
                    .build();
                player.sendActionBar(message);
                run.setLastCheckpointMessageMillis(now);
            }
            return;
        }
        
        // Check if player is in any other checkpoint (wrong checkpoint)
        for (CheckpointRegion cp : checkpoints) {
            int cpIndex = cp.getCheckpointIndex();
            if (cpIndex != nextRequired && !run.getPassedCheckpoints().contains(cpIndex)) {
                if (isInCheckpoint(playerLoc, cp)) {
                    // Wrong checkpoint - send message (with cooldown)
                    long now = System.currentTimeMillis();
                    if (now - run.getLastCheckpointMessageMillis() >= 1000) {
                        player.sendMessage("§cWrong checkpoint. Next: #" + nextRequired);
                        run.setLastCheckpointMessageMillis(now);
                        
                        // Debug log
                        Map<String, Object> kv = new HashMap<>();
                        kv.put("course", run.getCourseKey().getName());
                        kv.put("player", player.getName());
                        kv.put("entered", cpIndex);
                        kv.put("expected", nextRequired);
                        plugin.getDebugLog().info(DebugLog.Tag.RULE, "RaceDetection", "Wrong checkpoint", kv);
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * Update HUD (ActionBar with elapsed time and checkpoint progress)
     */
    private void updateHUD(Player player, RaceManager.ActiveRun run, 
                           boolean requireCheckpoints, int totalCheckpoints) {
        long elapsedMillis = run.getElapsedMillis();
        String timeStr = formatTime(elapsedMillis);
        
        net.kyori.adventure.text.ComponentBuilder<?, ?> hudBuilder = Component.text()
            .append(Component.text("Time: ", NamedTextColor.GRAY))
            .append(Component.text(timeStr, NamedTextColor.WHITE));
        
        if (requireCheckpoints && totalCheckpoints > 0) {
            int current = run.getNextRequiredCheckpointIndex() - 1; // Already passed count
            hudBuilder.append(Component.text(" | CP: ", NamedTextColor.GRAY))
                      .append(Component.text(current + "/" + totalCheckpoints, NamedTextColor.YELLOW));
        }
        
        player.sendActionBar(hudBuilder.build());
    }
    
    /**
     * Finish a race
     */
    private void finishRace(Player player, RaceManager.ActiveRun run, RaceManager.CourseKey courseKey) {
        long finishMillis = System.currentTimeMillis();
        run.setFinishMillis(finishMillis);
        long elapsedMillis = run.getElapsedMillis();
        String timeStr = formatTime(elapsedMillis);
        player.sendMessage("§a§lFinished! Time: §f" + timeStr);
        
        // Database: Finish run (async)
        if (plugin.getRunDao() != null) {
            plugin.getRunDao().finishRun(run.getRunId(), finishMillis, elapsedMillis, courseKey.getName(), run.getRacerUuid());
        }
        
        // Remove boat if player is in one (for BOAT courses)
        Course course = courseManager.findCourse(courseKey.getName());
        if (course != null && course.getType() == com.bocrace.model.CourseType.BOAT) {
            org.bukkit.entity.Boat boat = plugin.getBoatManager().findRaceBoatByPlayer(run.getRacerUuid());
            if (boat != null) {
                plugin.getBoatManager().removeRaceBoat(boat, "race_finished");
            }
        }
        
        // Teleport to course lobby if available
        if (course != null && course.getCourseLobbySpawn() != null) {
            Location lobbySpawn = course.getCourseLobbySpawn();
            // Ensure world is loaded
            if (lobbySpawn.getWorld() != null) {
                player.teleport(lobbySpawn);
                player.sendMessage("§7Teleported to course lobby.");
            }
        }
        
        // Debug log
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", courseKey.getName());
        kv.put("player", player.getName());
        kv.put("timeMs", elapsedMillis);
        kv.put("formatted", timeStr);
        plugin.getDebugLog().info(DebugLog.Tag.DETECT, "RaceDetection", "RUN_FINISH", kv);
        
        // Check if solo run - clear lock and run (cleanup handled here)
        Map<UUID, RaceManager.ActiveRun> courseRuns = raceManager.getActiveRuns(courseKey);
                        if (courseRuns.size() == 1) {
                            // Solo run finished - clear lock and run
                            raceManager.releaseSoloLock(courseKey, run.getRacerUuid());
                            raceManager.removeActiveRun(courseKey, run.getRacerUuid());
                            
                            // Debug log cleanup
                            Map<String, Object> cleanupKv = new HashMap<>();
                            cleanupKv.put("course", courseKey.getName());
                            cleanupKv.put("player", player.getName());
                            cleanupKv.put("reason", "finished");
                            plugin.getDebugLog().info(DebugLog.Tag.STATE, "RaceDetection", "RUN_CLEAR (SOLO finished)", cleanupKv);
                            // Note: drop blocks cleanup happens on return button or quit
                        } else {
            // MP run finished - check if all finished to cleanup lobby
            boolean allFinished = true;
            RaceManager.MultiLobbyState lobby = raceManager.getMultiLobby(courseKey);
            if (lobby != null && lobby.getState() == RaceManager.MultiLobbyState.LobbyState.IN_PROGRESS) {
                for (RaceManager.ActiveRun r : courseRuns.values()) {
                    if (!r.isFinished()) {
                        allFinished = false;
                        break;
                    }
                }
                                if (allFinished) {
                                    // All racers finished - cleanup
                                    raceManager.clearActiveRuns(courseKey);
                                    raceManager.clearMultiLobby(courseKey);
                                    
                                    // Debug log cleanup
                                    Map<String, Object> cleanupKv = new HashMap<>();
                                    cleanupKv.put("course", courseKey.getName());
                                    cleanupKv.put("reason", "all_finished");
                                    plugin.getDebugLog().info(DebugLog.Tag.STATE, "RaceDetection", "RUN_CLEAR (MP all finished)", cleanupKv);
                                    plugin.getDebugLog().info(DebugLog.Tag.STATE, "RaceDetection", "LOBBY_CLEAR (all finished)", cleanupKv);
                                    // Drop blocks cleanup happens via cancelAllDrops (no scheduled tasks left)
                                }
            }
        }
    }
    
    /**
     * Format milliseconds as mm:ss.SSS
     */
    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long milliseconds = millis % 1000; // Show milliseconds (three digits)
        
        return String.format("%02d:%02d.%03d", minutes, seconds, milliseconds);
    }
    
}
