package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.runtime.RaceManager;
import com.bocrace.storage.CourseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles player quit/kick to clean up race lobby state
 */
public class PlayerLifecycleListener implements Listener {
    
    private final BOCRacingV2 plugin;
    private final RaceManager raceManager;
    private final CourseManager courseManager;
    
    public PlayerLifecycleListener(BOCRacingV2 plugin, RaceManager raceManager, CourseManager courseManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.courseManager = courseManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerLeave(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerLeave(event.getPlayer());
    }
    
    private void handlePlayerLeave(Player player) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        
        // Find lobby
        RaceManager.LobbyResult result = raceManager.findLobbyByPlayer(playerUuid);
        if (result == null) {
            return; // Not in any lobby
        }
        
        RaceManager.CourseKey key = result.getCourseKey();
        RaceManager.MultiLobbyState lobby = result.getLobby();
        
        boolean wasLeader = lobby.getLeaderUuid() != null && lobby.getLeaderUuid().equals(playerUuid);
        int playerCountBefore = lobby.getJoinedPlayers().size();
        if (wasLeader && !lobby.getJoinedPlayers().containsKey(playerUuid)) {
            playerCountBefore++; // Count leader if not in joinedPlayers
        }
        
        // Remove player
        raceManager.removePlayerFromLobby(playerUuid, "Player left");
        
        // Check if lobby still exists (might have been deleted if empty)
        RaceManager.MultiLobbyState updatedLobby = raceManager.getMultiLobby(key);
        if (updatedLobby == null) {
            // Lobby was deleted (became empty)
            return;
        }
        
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
