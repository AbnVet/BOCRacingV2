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
     * Clear all state (on plugin disable)
     */
    public void clearAll() {
        activeSoloLocks.clear();
        activeMultiLobbies.clear();
    }
}
