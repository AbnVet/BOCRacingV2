package com.bocrace.runtime;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages runtime race state (in-memory only, no persistence)
 */
public class RaceManager {
    
    /**
     * Course key for maps (type + name)
     */
    public static class CourseKey {
        private final String type;
        private final String name;
        
        public CourseKey(String type, String name) {
            this.type = type;
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public String getName() {
            return name;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CourseKey courseKey = (CourseKey) o;
            return Objects.equals(type, courseKey.type) && Objects.equals(name, courseKey.name);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }
    }
    
    /**
     * Solo lock (expires after 120 seconds)
     */
    public static class SoloLock {
        private final long expiresAtMillis;
        private final UUID playerUuid;
        
        public SoloLock(UUID playerUuid, long lockDurationSeconds) {
            this.playerUuid = playerUuid;
            this.expiresAtMillis = System.currentTimeMillis() + (lockDurationSeconds * 1000);
        }
        
        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }
        
        public UUID getPlayerUuid() {
            return playerUuid;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
        
        public long getRemainingSeconds() {
            long remaining = (expiresAtMillis - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        }
    }
    
    /**
     * Multiplayer lobby state
     */
    public static class MultiLobbyState {
        private final CourseKey courseKey;
        private final Map<UUID, Integer> joinedPlayers; // playerUuid -> spawnIndex
        private final Set<Integer> usedSpawnIndices;
        private UUID leaderUuid;
        private final long createdAtMillis;
        private LobbyState state;
        
        public enum LobbyState {
            OPEN,
            STARTING,
            IN_PROGRESS
        }
        
        public MultiLobbyState(CourseKey courseKey) {
            this.courseKey = courseKey;
            this.joinedPlayers = new HashMap<>();
            this.usedSpawnIndices = new HashSet<>();
            this.createdAtMillis = System.currentTimeMillis();
            this.state = LobbyState.OPEN;
        }
        
        public CourseKey getCourseKey() {
            return courseKey;
        }
        
        public Map<UUID, Integer> getJoinedPlayers() {
            return joinedPlayers;
        }
        
        public Set<Integer> getUsedSpawnIndices() {
            return usedSpawnIndices;
        }
        
        public UUID getLeaderUuid() {
            return leaderUuid;
        }
        
        public void setLeaderUuid(UUID leaderUuid) {
            this.leaderUuid = leaderUuid;
        }
        
        public long getCreatedAtMillis() {
            return createdAtMillis;
        }
        
        public LobbyState getState() {
            return state;
        }
        
        public void setState(LobbyState state) {
            this.state = state;
        }
    }
    
    private final Map<CourseKey, SoloLock> activeSoloLocks;
    private final Map<CourseKey, MultiLobbyState> activeMultiLobbies;
    
    public RaceManager() {
        this.activeSoloLocks = new HashMap<>();
        this.activeMultiLobbies = new HashMap<>();
    }
    
    /**
     * Get or create solo lock (cleans expired locks on access)
     */
    public SoloLock getSoloLock(CourseKey key) {
        SoloLock lock = activeSoloLocks.get(key);
        if (lock != null && lock.isExpired()) {
            activeSoloLocks.remove(key);
            return null;
        }
        return lock;
    }
    
    /**
     * Acquire solo lock
     */
    public void acquireSoloLock(CourseKey key, UUID playerUuid, long lockDurationSeconds) {
        activeSoloLocks.put(key, new SoloLock(playerUuid, lockDurationSeconds));
    }
    
    /**
     * Release solo lock (only if player holds it)
     */
    public boolean releaseSoloLock(CourseKey key, UUID playerUuid) {
        SoloLock lock = activeSoloLocks.get(key);
        if (lock != null && lock.getPlayerUuid().equals(playerUuid)) {
            activeSoloLocks.remove(key);
            return true;
        }
        return false;
    }
    
    /**
     * Get or create multiplayer lobby
     */
    public MultiLobbyState getOrCreateMultiLobby(CourseKey key) {
        return activeMultiLobbies.computeIfAbsent(key, MultiLobbyState::new);
    }
    
    /**
     * Get multiplayer lobby (null if doesn't exist)
     */
    public MultiLobbyState getMultiLobby(CourseKey key) {
        return activeMultiLobbies.get(key);
    }
    
    /**
     * Clear multiplayer lobby
     */
    public void clearMultiLobby(CourseKey key) {
        activeMultiLobbies.remove(key);
    }
    
    /**
     * Find lobby by player UUID
     * @return Pair of (CourseKey, MultiLobbyState) or null if not found
     */
    public static class LobbyResult {
        private final CourseKey courseKey;
        private final MultiLobbyState lobby;
        
        public LobbyResult(CourseKey courseKey, MultiLobbyState lobby) {
            this.courseKey = courseKey;
            this.lobby = lobby;
        }
        
        public CourseKey getCourseKey() {
            return courseKey;
        }
        
        public MultiLobbyState getLobby() {
            return lobby;
        }
    }
    
    public LobbyResult findLobbyByPlayer(UUID playerUuid) {
        for (Map.Entry<CourseKey, MultiLobbyState> entry : activeMultiLobbies.entrySet()) {
            MultiLobbyState lobby = entry.getValue();
            if (lobby.getJoinedPlayers().containsKey(playerUuid) || 
                (lobby.getLeaderUuid() != null && lobby.getLeaderUuid().equals(playerUuid))) {
                return new LobbyResult(entry.getKey(), lobby);
            }
        }
        return null;
    }
    
    /**
     * Remove player from lobby and free their spawn
     * @return true if player was removed, false if not found
     */
    public boolean removePlayerFromLobby(UUID playerUuid, String reasonMessage) {
        LobbyResult result = findLobbyByPlayer(playerUuid);
        if (result == null) {
            return false;
        }
        
        MultiLobbyState lobby = result.getLobby();
        boolean wasInLobby = lobby.getJoinedPlayers().containsKey(playerUuid);
        boolean wasLeader = lobby.getLeaderUuid() != null && lobby.getLeaderUuid().equals(playerUuid);
        
        // Remove from joined players and free spawn
        if (wasInLobby) {
            Integer spawnIndex = lobby.getJoinedPlayers().remove(playerUuid);
            if (spawnIndex != null) {
                lobby.getUsedSpawnIndices().remove(spawnIndex);
            }
        }
        
        // Handle leader reassignment or lobby deletion
        if (wasLeader) {
            lobby.setLeaderUuid(null);
            
            if (lobby.getJoinedPlayers().isEmpty()) {
                // Lobby empty, delete it
                activeMultiLobbies.remove(result.getCourseKey());
                return true;
            } else {
                // Assign new leader (first player in keyset)
                UUID newLeader = lobby.getJoinedPlayers().keySet().iterator().next();
                lobby.setLeaderUuid(newLeader);
                // Notify will be done by caller
            }
        }
        
        return wasInLobby || wasLeader;
    }
    
    /**
     * Clear all state (on plugin disable)
     */
    public void clearAll() {
        activeSoloLocks.clear();
        activeMultiLobbies.clear();
    }
}
