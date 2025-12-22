package com.bocrace.setup;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages setup sessions for admins
 * Tracks armed actions and pending region points
 */
public class SetupSessionManager {
    
    private final Map<UUID, SetupSession> sessions;
    
    public SetupSessionManager() {
        this.sessions = new HashMap<>();
    }
    
    /**
     * Start a setup session for a player
     */
    public void startSession(Player player, String courseName, SetupSession.ArmedAction action) {
        sessions.put(player.getUniqueId(), new SetupSession(courseName, action));
    }
    
    /**
     * Get the current session for a player
     */
    public SetupSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }
    
    /**
     * Check if player has an active session
     */
    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }
    
    /**
     * Clear a player's session
     */
    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
    }
    
    /**
     * Clear all sessions (on plugin disable)
     */
    public void clearAll() {
        sessions.clear();
    }
}
