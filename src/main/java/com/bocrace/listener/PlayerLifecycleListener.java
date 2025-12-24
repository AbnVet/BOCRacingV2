package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.runtime.DropBlockManager;
import com.bocrace.runtime.RaceManager;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player quit/kick to clean up race lobby state
 */
public class PlayerLifecycleListener implements Listener {
    
    private final BOCRacingV2 plugin;
    private final RaceManager raceManager;
    private final CourseManager courseManager;
    private final DropBlockManager dropBlockManager;
    
    public PlayerLifecycleListener(BOCRacingV2 plugin, RaceManager raceManager, CourseManager courseManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.courseManager = courseManager;
        this.dropBlockManager = plugin.getDropBlockManager();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerLeave(event.getPlayer(), "quit");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerLeave(event.getPlayer(), "kick");
    }
    
    private void handlePlayerLeave(Player player, String reason) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        
        // Track what was cleaned up
        boolean cleanedSoloLock = false;
        boolean cleanedRuns = false;
        boolean cleanedLobby = false;
        String cleanedCourse = null;
        
        // Clear solo locks held by this player
        int soloLocksCleared = 0;
        for (RaceManager.CourseKey key : new ArrayList<>(raceManager.getActiveRunsMap().keySet())) {
            if (raceManager.getSoloLock(key) != null && raceManager.getSoloLock(key).getPlayerUuid().equals(playerUuid)) {
                soloLocksCleared++;
                cleanedSoloLock = true;
                cleanedCourse = key.getName();
            }
        }
        raceManager.clearSoloLockIfHeldBy(playerUuid);
        
        // Clear active solo runs for this player
        for (RaceManager.CourseKey key : new ArrayList<>(raceManager.getActiveRunsMap().keySet())) {
            RaceManager.ActiveRun run = raceManager.getActiveRun(key, playerUuid);
            if (run != null) {
                // Check if it's a solo run (only 1 player in course runs)
                Map<UUID, RaceManager.ActiveRun> courseRuns = raceManager.getActiveRuns(key);
                if (courseRuns.size() == 1) {
                    // Database: Abort solo run (async)
                    if (plugin.getRunDao() != null) {
                        plugin.getRunDao().abortRun(run.getRunId(), "Player left: " + reason);
                    }
                    
                    dropBlockManager.cancelAllDrops(key);
                    raceManager.removeActiveRun(key, playerUuid);
                    cleanedRuns = true;
                    cleanedCourse = key.getName();
                    break;
                }
            }
        }
        
        // Find lobby
        RaceManager.LobbyResult result = raceManager.findLobbyByPlayer(playerUuid);
        if (result == null) {
            // Debug log cleanup (even if no lobby)
            if (cleanedSoloLock || cleanedRuns) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("player", playerName);
                kv.put("reason", reason);
                kv.put("cleanedSoloLock", cleanedSoloLock);
                kv.put("cleanedRuns", cleanedRuns);
                kv.put("cleanedLobby", false);
                if (cleanedCourse != null) {
                    kv.put("course", cleanedCourse);
                }
                plugin.getDebugLog().info(DebugLog.Tag.STATE, "PlayerLifecycleListener", "PLAYER_LEAVE", kv);
            }
            return; // Not in any lobby
        }
        
        RaceManager.CourseKey key = result.getCourseKey();
        RaceManager.MultiLobbyState lobby = result.getLobby();
        cleanedCourse = key.getName();
        
        boolean wasLeader = lobby.getLeaderUuid() != null && lobby.getLeaderUuid().equals(playerUuid);
        int playerCountBefore = lobby.getJoinedPlayers().size();
        if (wasLeader && !lobby.getJoinedPlayers().containsKey(playerUuid)) {
            playerCountBefore++; // Count leader if not in joinedPlayers
        }
        
        // Remove active run for this player
        RaceManager.ActiveRun run = raceManager.getActiveRun(key, playerUuid);
        if (run != null) {
            cleanedRuns = true;
            // Database: Abort MP run (async)
            if (plugin.getRunDao() != null) {
                plugin.getRunDao().abortRun(run.getRunId(), "Player left: " + reason);
            }
        }
        raceManager.removeActiveRun(key, playerUuid);
        
        // Remove player
        raceManager.removePlayerFromLobby(playerUuid, "Player left");
        
        // Check if lobby still exists (might have been deleted if empty)
        RaceManager.MultiLobbyState updatedLobby = raceManager.getMultiLobby(key);
        if (updatedLobby == null) {
            // Lobby was deleted (became empty) - abort all runs and cancel any pending drops
            if (plugin.getRunDao() != null) {
                Map<UUID, RaceManager.ActiveRun> runs = raceManager.getActiveRuns(key);
                for (RaceManager.ActiveRun r : runs.values()) {
                    plugin.getRunDao().abortRun(r.getRunId(), "Lobby emptied: " + reason);
                }
            }
            dropBlockManager.cancelAllDrops(key);
            raceManager.clearActiveRuns(key);
            cleanedLobby = true;
            
            // Debug log cleanup
            Map<String, Object> kv = new HashMap<>();
            kv.put("player", playerName);
            kv.put("reason", reason);
            kv.put("cleanedSoloLock", cleanedSoloLock);
            kv.put("cleanedRuns", cleanedRuns);
            kv.put("cleanedLobby", cleanedLobby);
            kv.put("course", cleanedCourse);
            plugin.getDebugLog().info(DebugLog.Tag.STATE, "PlayerLifecycleListener", "PLAYER_LEAVE", kv);
            return;
        }
        
        // Check if all racers finished or left - cleanup if needed (only if in progress)
        if (updatedLobby.getState() == RaceManager.MultiLobbyState.LobbyState.IN_PROGRESS) {
            Map<UUID, RaceManager.ActiveRun> activeRuns = raceManager.getActiveRuns(key);
            if (activeRuns.isEmpty()) {
                // All racers gone, cleanup (runs already aborted above)
                dropBlockManager.cancelAllDrops(key);
                raceManager.clearActiveRuns(key);
                raceManager.clearMultiLobby(key);
                cleanedLobby = true;
                
                // Debug log cleanup
                Map<String, Object> kv = new HashMap<>();
                kv.put("player", playerName);
                kv.put("reason", reason);
                kv.put("cleanedSoloLock", cleanedSoloLock);
                kv.put("cleanedRuns", cleanedRuns);
                kv.put("cleanedLobby", cleanedLobby);
                kv.put("course", cleanedCourse);
                plugin.getDebugLog().info(DebugLog.Tag.STATE, "PlayerLifecycleListener", "PLAYER_LEAVE", kv);
                return;
            }
        }
        
        // Debug log cleanup (partial cleanup, lobby still exists)
        Map<String, Object> kv = new HashMap<>();
        kv.put("player", playerName);
        kv.put("reason", reason);
        kv.put("cleanedSoloLock", cleanedSoloLock);
        kv.put("cleanedRuns", cleanedRuns);
        kv.put("cleanedLobby", false);
        kv.put("course", cleanedCourse);
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "PlayerLifecycleListener", "PLAYER_LEAVE", kv);
        
        // Notify remaining players
        int playerCountAfter = updatedLobby.getJoinedPlayers().size();
        if (updatedLobby.getLeaderUuid() != null && !updatedLobby.getJoinedPlayers().containsKey(updatedLobby.getLeaderUuid())) {
            playerCountAfter++; // Count leader if not in joinedPlayers
        }
        
        Course course = courseManager.findCourse(key.getName());
        int maxPlayers = course != null ? course.getPlayerSpawns().size() : playerCountAfter;
        
        // Notify players in lobby
        for (UUID uuid : updatedLobby.getJoinedPlayers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§7" + playerName + " left (" + playerCountAfter + "/" + maxPlayers + ").");
            }
        }
        
        // Notify new leader if leader changed
        if (wasLeader && updatedLobby.getLeaderUuid() != null) {
            Player newLeader = Bukkit.getPlayer(updatedLobby.getLeaderUuid());
            if (newLeader != null) {
                newLeader.sendMessage("§6New leader: " + newLeader.getName() + ".");
                // Also notify others
                for (UUID uuid : updatedLobby.getJoinedPlayers().keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && !p.equals(newLeader)) {
                        p.sendMessage("§6New leader: " + newLeader.getName() + ".");
                    }
                }
            }
        }
    }
}
