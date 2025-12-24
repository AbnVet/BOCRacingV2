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
    private final Map<CourseKey, Map<UUID, ActiveRun>> activeRuns;
    
    public RaceManager() {
        this.activeSoloLocks = new HashMap<>();
        this.activeMultiLobbies = new HashMap<>();
        this.activeRuns = new HashMap<>();
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
     * Clear solo lock if held by player (used for quit/kick cleanup)
     */
    public void clearSoloLockIfHeldBy(UUID playerUuid) {
        activeSoloLocks.entrySet().removeIf(entry -> {
            SoloLock lock = entry.getValue();
            return lock.getPlayerUuid().equals(playerUuid);
        });
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
     * Active race run for a single racer
     */
    public static class ActiveRun {
        private final CourseKey courseKey;
        private final UUID racerUuid;
        private final int spawnIndex;
        private Long startMillis; // null until started
        private Long finishMillis; // null until finished
        private boolean started;
        private boolean finished;
        
        // Checkpoint tracking
        private int nextRequiredCheckpointIndex; // Next checkpoint that must be passed (1-based)
        private final java.util.Set<Integer> passedCheckpoints; // Set of checkpoint indices that have been passed
        private final java.util.Map<Integer, Long> checkpointSplitTimes; // checkpointIndex -> millis since start
        private long lastCheckpointMessageMillis; // Anti-spam cooldown for checkpoint messages
        
        public ActiveRun(CourseKey courseKey, UUID racerUuid, int spawnIndex) {
            this.courseKey = courseKey;
            this.racerUuid = racerUuid;
            this.spawnIndex = spawnIndex;
            this.started = false;
            this.finished = false;
            this.nextRequiredCheckpointIndex = 1;
            this.passedCheckpoints = new java.util.HashSet<>();
            this.checkpointSplitTimes = new java.util.HashMap<>();
            this.lastCheckpointMessageMillis = 0;
        }
        
        public CourseKey getCourseKey() {
            return courseKey;
        }
        
        public UUID getRacerUuid() {
            return racerUuid;
        }
        
        public int getSpawnIndex() {
            return spawnIndex;
        }
        
        public Long getStartMillis() {
            return startMillis;
        }
        
        public void setStartMillis(long startMillis) {
            this.startMillis = startMillis;
            this.started = true;
            // Initialize checkpoint tracking when timer starts
            this.nextRequiredCheckpointIndex = 1;
        }
        
        public Long getFinishMillis() {
            return finishMillis;
        }
        
        public void setFinishMillis(long finishMillis) {
            this.finishMillis = finishMillis;
            this.finished = true;
        }
        
        public boolean isStarted() {
            return started;
        }
        
        public boolean isFinished() {
            return finished;
        }
        
        public long getElapsedMillis() {
            if (startMillis == null) return 0;
            long endTime = finishMillis != null ? finishMillis : System.currentTimeMillis();
            return endTime - startMillis;
        }
        
        public int getNextRequiredCheckpointIndex() {
            return nextRequiredCheckpointIndex;
        }
        
        public void setNextRequiredCheckpointIndex(int nextRequiredCheckpointIndex) {
            this.nextRequiredCheckpointIndex = nextRequiredCheckpointIndex;
        }
        
        public java.util.Set<Integer> getPassedCheckpoints() {
            return passedCheckpoints;
        }
        
        public java.util.Map<Integer, Long> getCheckpointSplitTimes() {
            return checkpointSplitTimes;
        }
        
        public long getLastCheckpointMessageMillis() {
            return lastCheckpointMessageMillis;
        }
        
        public void setLastCheckpointMessageMillis(long lastCheckpointMessageMillis) {
            this.lastCheckpointMessageMillis = lastCheckpointMessageMillis;
        }
    }
    
    /**
     * Get active run for a racer
     */
    public ActiveRun getActiveRun(CourseKey key, UUID racerUuid) {
        Map<UUID, ActiveRun> runs = activeRuns.get(key);
        if (runs == null) return null;
        return runs.get(racerUuid);
    }
    
    /**
     * Get all active runs for a course
     */
    public Map<UUID, ActiveRun> getActiveRuns(CourseKey key) {
        return activeRuns.computeIfAbsent(key, k -> new HashMap<>());
    }
    
    /**
     * Get all active runs map (for detection task)
     */
    public Map<CourseKey, Map<UUID, ActiveRun>> getActiveRunsMap() {
        return activeRuns;
    }
    
    /**
     * Create an active run
     */
    public ActiveRun createActiveRun(CourseKey key, UUID racerUuid, int spawnIndex) {
        Map<UUID, ActiveRun> runs = activeRuns.computeIfAbsent(key, k -> new HashMap<>());
        ActiveRun run = new ActiveRun(key, racerUuid, spawnIndex);
        runs.put(racerUuid, run);
        return run;
    }
    
    /**
     * Remove active run
     */
    public ActiveRun removeActiveRun(CourseKey key, UUID racerUuid) {
        Map<UUID, ActiveRun> runs = activeRuns.get(key);
        if (runs == null) return null;
        ActiveRun removed = runs.remove(racerUuid);
        if (runs.isEmpty()) {
            activeRuns.remove(key);
        }
        return removed;
    }
    
    /**
     * Clear all active runs for a course
     */
    public void clearActiveRuns(CourseKey key) {
        activeRuns.remove(key);
    }
    
    /**
     * Clear all state (on plugin disable)
     */
    public void clearAll() {
        activeSoloLocks.clear();
        activeMultiLobbies.clear();
        activeRuns.clear();
    }
}
