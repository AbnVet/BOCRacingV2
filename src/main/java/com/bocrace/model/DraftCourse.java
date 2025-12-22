package com.bocrace.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a course draft during admin setup
 * Persisted as JSON in plugins/BOCRacingV2/drafts/
 */
public class DraftCourse {
    
    private String name;
    private CourseType type;
    private CourseStatus status;
    
    // Single course lobby spawn (with yaw, pitch=0)
    private Location courseLobbySpawn;
    
    // List of player spawns (each with yaw, pitch=0)
    private List<Location> playerSpawns;
    
    // Start region (two block coordinates)
    private BlockCoord startPoint1;
    private BlockCoord startPoint2;
    
    // Finish region (two block coordinates)
    private BlockCoord finishPoint1;
    private BlockCoord finishPoint2;
    
    // Checkpoints (each with two block coordinates and index)
    private List<CheckpointRegion> checkpoints;
    
    public DraftCourse() {
        this.status = CourseStatus.DRAFT;
        this.playerSpawns = new ArrayList<>();
        this.checkpoints = new ArrayList<>();
    }
    
    public DraftCourse(String name, CourseType type) {
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
    
    public CourseStatus getStatus() {
        return status;
    }
    
    public void setStatus(CourseStatus status) {
        this.status = status;
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
    
    public BlockCoord getStartPoint1() {
        return startPoint1;
    }
    
    public void setStartPoint1(BlockCoord startPoint1) {
        this.startPoint1 = startPoint1;
    }
    
    public BlockCoord getStartPoint2() {
        return startPoint2;
    }
    
    public void setStartPoint2(BlockCoord startPoint2) {
        this.startPoint2 = startPoint2;
    }
    
    public BlockCoord getFinishPoint1() {
        return finishPoint1;
    }
    
    public void setFinishPoint1(BlockCoord finishPoint1) {
        this.finishPoint1 = finishPoint1;
    }
    
    public BlockCoord getFinishPoint2() {
        return finishPoint2;
    }
    
    public void setFinishPoint2(BlockCoord finishPoint2) {
        this.finishPoint2 = finishPoint2;
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
