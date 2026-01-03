package com.bocrace.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a course configuration
 * Persisted as YAML in plugins/BOCRacingV2/boatracing/ or airracing/
 */
public class Course {
    
    /**
     * Start mode for races
     */
    public enum StartMode {
        CROSS_LINE,  // Timer starts only when crossing start trigger
        DROP_START   // Timer starts at GO; blocks under racers drop briefly
    }
    
    /**
     * Course mode (SOLO or MULTIPLAYER)
     */
    public enum Mode {
        SOLO,        // Single player course (1 spawn)
        MULTIPLAYER  // Multiplayer course (2+ spawns)
    }
    
    private String name;
    private CourseType type;
    private Mode mode; // Explicit mode (SOLO or MULTIPLAYER)
    private CourseSettings settings;
    
    // Single course lobby spawn (with yaw, pitch=0)
    private Location courseLobbySpawn;
    
    // List of player spawns (each with yaw, pitch=0)
    private List<Location> playerSpawns;
    
    // Start region (ground volume with height=2)
    private VolumeRegion startRegion;
    
    // Finish region (ground volume with height=2)
    private VolumeRegion finishRegion;
    
    // Checkpoints (each with two block coordinates and index)
    private List<CheckpointRegion> checkpoints;
    
    // SOLO buttons
    private BlockCoord soloJoinButton; // Mandatory for solo
    private BlockCoord soloReturnButton; // Optional
    
    // MULTI course items
    private Location mpLobby; // Where players return if cancelled
    private BlockCoord mpJoinButton; // Mandatory for multiplayer
    private BlockCoord mpLeaderCreateButton; // Optional
    private BlockCoord mpLeaderStartButton; // Mandatory
    private BlockCoord mpLeaderCancelButton; // Mandatory
    
    // Boat type (for boat races only)
    private String boatType; // null = use default (OAK_BOAT), e.g. "OAK_BOAT", "BIRCH_BOAT", etc.
    
    public Course() {
        this.playerSpawns = new ArrayList<>();
        this.checkpoints = new ArrayList<>();
        this.settings = new CourseSettings(); // Default settings
    }
    
    public Course(String name, CourseType type) {
        this();
        this.name = name;
        this.type = type;
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public CourseType getType() {
        return type;
    }
    
    public void setType(CourseType type) {
        this.type = type;
    }
    
    public Mode getMode() {
        return mode;
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
    }
    
    /**
     * Derive mode from spawn count if mode is not explicitly set
     */
    public Mode getModeOrDefault() {
        if (mode != null) {
            return mode;
        }
        // Fallback: derive from spawn count
        int spawnCount = playerSpawns != null ? playerSpawns.size() : 0;
        return spawnCount == 1 ? Mode.SOLO : Mode.MULTIPLAYER;
    }
    
    public Location getCourseLobbySpawn() {
        return courseLobbySpawn;
    }
    
    public void setCourseLobbySpawn(Location courseLobbySpawn) {
        this.courseLobbySpawn = courseLobbySpawn;
    }
    
    public List<Location> getPlayerSpawns() {
        return playerSpawns;
    }
    
    public void setPlayerSpawns(List<Location> playerSpawns) {
        this.playerSpawns = playerSpawns;
    }
    
    public void addPlayerSpawn(Location spawn) {
        this.playerSpawns.add(spawn);
    }
    
    public VolumeRegion getStartRegion() {
        return startRegion;
    }
    
    public void setStartRegion(VolumeRegion startRegion) {
        this.startRegion = startRegion;
    }
    
    public VolumeRegion getFinishRegion() {
        return finishRegion;
    }
    
    public void setFinishRegion(VolumeRegion finishRegion) {
        this.finishRegion = finishRegion;
    }
    
    
    public List<CheckpointRegion> getCheckpoints() {
        return checkpoints;
    }
    
    public void setCheckpoints(List<CheckpointRegion> checkpoints) {
        this.checkpoints = checkpoints;
    }
    
    public void addCheckpoint(CheckpointRegion checkpoint) {
        this.checkpoints.add(checkpoint);
    }
    
    // SOLO button getters/setters
    public BlockCoord getSoloJoinButton() {
        return soloJoinButton;
    }
    
    public void setSoloJoinButton(BlockCoord soloJoinButton) {
        this.soloJoinButton = soloJoinButton;
    }
    
    public BlockCoord getSoloReturnButton() {
        return soloReturnButton;
    }
    
    public void setSoloReturnButton(BlockCoord soloReturnButton) {
        this.soloReturnButton = soloReturnButton;
    }
    
    // MULTI getters/setters
    public Location getMpLobby() {
        return mpLobby;
    }
    
    public void setMpLobby(Location mpLobby) {
        this.mpLobby = mpLobby;
    }
    
    public BlockCoord getMpJoinButton() {
        return mpJoinButton;
    }
    
    public void setMpJoinButton(BlockCoord mpJoinButton) {
        this.mpJoinButton = mpJoinButton;
    }
    
    public BlockCoord getMpLeaderCreateButton() {
        return mpLeaderCreateButton;
    }
    
    public void setMpLeaderCreateButton(BlockCoord mpLeaderCreateButton) {
        this.mpLeaderCreateButton = mpLeaderCreateButton;
    }
    
    public BlockCoord getMpLeaderStartButton() {
        return mpLeaderStartButton;
    }
    
    public void setMpLeaderStartButton(BlockCoord mpLeaderStartButton) {
        this.mpLeaderStartButton = mpLeaderStartButton;
    }
    
    public BlockCoord getMpLeaderCancelButton() {
        return mpLeaderCancelButton;
    }
    
    public void setMpLeaderCancelButton(BlockCoord mpLeaderCancelButton) {
        this.mpLeaderCancelButton = mpLeaderCancelButton;
    }
    
    public String getBoatType() {
        return boatType;
    }
    
    public void setBoatType(String boatType) {
        this.boatType = boatType;
    }
    
    public CourseSettings getSettings() {
        return settings;
    }
    
    public void setSettings(CourseSettings settings) {
        this.settings = settings;
    }
    
    /**
     * Course settings with defaults
     */
    public static class CourseSettings {
        private StartMode startMode = StartMode.CROSS_LINE;
        private int countdownSeconds = 5;
        private int soloCooldownSeconds = 120;
        private DropSettings drop = new DropSettings();
        private RulesSettings rules = new RulesSettings();
        
        public StartMode getStartMode() {
            return startMode;
        }
        
        public void setStartMode(StartMode startMode) {
            this.startMode = startMode;
        }
        
        public int getCountdownSeconds() {
            return countdownSeconds;
        }
        
        public void setCountdownSeconds(int countdownSeconds) {
            this.countdownSeconds = countdownSeconds;
        }
        
        public int getSoloCooldownSeconds() {
            return soloCooldownSeconds;
        }
        
        public void setSoloCooldownSeconds(int soloCooldownSeconds) {
            this.soloCooldownSeconds = soloCooldownSeconds;
        }
        
        public DropSettings getDrop() {
            return drop;
        }
        
        public void setDrop(DropSettings drop) {
            this.drop = drop;
        }
        
        public RulesSettings getRules() {
            return rules;
        }
        
        public void setRules(RulesSettings rules) {
            this.rules = rules;
        }
    }
    
    /**
     * Drop start settings
     */
    public static class DropSettings {
        public enum DropShape {
            SINGLE,
            SQUARE,
            CIRCLE
        }
        
        private DropShape shape = DropShape.SINGLE;
        private int radius = 1;
        private int restoreSeconds = 10;
        
        public DropShape getShape() {
            return shape;
        }
        
        public void setShape(DropShape shape) {
            this.shape = shape;
        }
        
        public int getRadius() {
            return radius;
        }
        
        public void setRadius(int radius) {
            this.radius = radius;
        }
        
        public int getRestoreSeconds() {
            return restoreSeconds;
        }
        
        public void setRestoreSeconds(int restoreSeconds) {
            this.restoreSeconds = restoreSeconds;
        }
    }
    
    /**
     * Rules settings
     */
    public static class RulesSettings {
        private boolean requireCheckpoints = false;
        
        public boolean isRequireCheckpoints() {
            return requireCheckpoints;
        }
        
        public void setRequireCheckpoints(boolean requireCheckpoints) {
            this.requireCheckpoints = requireCheckpoints;
        }
    }
    
    /**
     * Block coordinate (integer coords for regions)
     */
    public static class BlockCoord {
        private String world;
        private int x;
        private int y;
        private int z;
        
        public BlockCoord() {}
        
        public BlockCoord(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public String getWorld() {
            return world;
        }
        
        public void setWorld(String world) {
            this.world = world;
        }
        
        public int getX() {
            return x;
        }
        
        public void setX(int x) {
            this.x = x;
        }
        
        public int getY() {
            return y;
        }
        
        public void setY(int y) {
            this.y = y;
        }
        
        public int getZ() {
            return z;
        }
        
        public void setZ(int z) {
            this.z = z;
        }
    }
    
    /**
     * Volume region with min/max bounds (for start/finish with height=2)
     */
    public static class VolumeRegion {
        private String world;
        private BlockCoord min;
        private BlockCoord max;
        
        public VolumeRegion() {}
        
        public VolumeRegion(String world, BlockCoord min, BlockCoord max) {
            this.world = world;
            this.min = min;
            this.max = max;
        }
        
        public String getWorld() {
            return world;
        }
        
        public void setWorld(String world) {
            this.world = world;
        }
        
        public BlockCoord getMin() {
            return min;
        }
        
        public void setMin(BlockCoord min) {
            this.min = min;
        }
        
        public BlockCoord getMax() {
            return max;
        }
        
        public void setMax(BlockCoord max) {
            this.max = max;
        }
    }
    
    /**
     * Checkpoint region with two points and index
     */
    public static class CheckpointRegion {
        private int checkpointIndex;
        private BlockCoord point1;
        private BlockCoord point2;
        
        public CheckpointRegion() {}
        
        public CheckpointRegion(int checkpointIndex, BlockCoord point1, BlockCoord point2) {
            this.checkpointIndex = checkpointIndex;
            this.point1 = point1;
            this.point2 = point2;
        }
        
        public int getCheckpointIndex() {
            return checkpointIndex;
        }
        
        public void setCheckpointIndex(int checkpointIndex) {
            this.checkpointIndex = checkpointIndex;
        }
        
        public BlockCoord getPoint1() {
            return point1;
        }
        
        public void setPoint1(BlockCoord point1) {
            this.point1 = point1;
        }
        
        public BlockCoord getPoint2() {
            return point2;
        }
        
        public void setPoint2(BlockCoord point2) {
            this.point2 = point2;
        }
    }
    
}
