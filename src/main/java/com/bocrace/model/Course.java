package com.bocrace.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a course configuration
 * Persisted as YAML in plugins/BOCRacingV2/boatracing/ or airracing/
 */
public class Course {
    
    private String name;
    private CourseType type;
    
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
    
    public Course() {
        this.playerSpawns = new ArrayList<>();
        this.checkpoints = new ArrayList<>();
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
