package com.bocrace.storage;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.CourseStatus;
import com.bocrace.model.CourseType;
import com.bocrace.model.DraftCourse;
import com.bocrace.model.DraftCourse.BlockCoord;
import com.bocrace.model.DraftCourse.CheckpointRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages course persistence as YAML files
 * boatrace -> plugins/BOCRacingV2/boatracing/<safeName>.yml
 * airrace  -> plugins/BOCRacingV2/airracing/<safeName>.yml
 */
public class CourseManager {
    
    private final BOCRacingV2 plugin;
    private final File boatRacingFolder;
    private final File airRacingFolder;
    
    public CourseManager(BOCRacingV2 plugin) {
        this.plugin = plugin;
        
        // Create folders if they don't exist
        this.boatRacingFolder = new File(plugin.getDataFolder(), "boatracing");
        this.airRacingFolder = new File(plugin.getDataFolder(), "airracing");
        
        if (!boatRacingFolder.exists()) {
            boatRacingFolder.mkdirs();
        }
        if (!airRacingFolder.exists()) {
            airRacingFolder.mkdirs();
        }
    }
    
    /**
     * Convert course name to safe filename
     */
    public static String toSafeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "").replace(" ", "_");
    }
    
    /**
     * Get course file for a given type and name
     */
    public File getCourseFile(CourseType type, String safeFileName) {
        File folder = type == CourseType.BOAT ? boatRacingFolder : airRacingFolder;
        return new File(folder, safeFileName + ".yml");
    }
    
    /**
     * Get folder for course type
     */
    private File getFolder(CourseType type) {
        return type == CourseType.BOAT ? boatRacingFolder : airRacingFolder;
    }
    
    /**
     * Save a course to YAML file
     */
    public void saveCourse(DraftCourse course) throws IOException {
        String safeFileName = toSafeFileName(course.getName());
        File file = getCourseFile(course.getType(), safeFileName);
        
        FileConfiguration config = new YamlConfiguration();
        
        // Basic info
        config.set("displayName", course.getName());
        config.set("fileName", safeFileName);
        config.set("type", course.getType().name());
        config.set("status", course.getStatus().name());
        
        // Course lobby spawn
        if (course.getCourseLobbySpawn() != null) {
            Location loc = course.getCourseLobbySpawn();
            config.set("courseLobby.world", loc.getWorld().getName());
            config.set("courseLobby.x", loc.getX());
            config.set("courseLobby.y", loc.getY());
            config.set("courseLobby.z", loc.getZ());
            config.set("courseLobby.yaw", loc.getYaw());
            config.set("courseLobby.pitch", loc.getPitch());
        }
        
        // Player spawns
        List<Location> spawns = course.getPlayerSpawns();
        if (spawns != null && !spawns.isEmpty()) {
            for (int i = 0; i < spawns.size(); i++) {
                Location loc = spawns.get(i);
                String path = "playerSpawns." + i;
                config.set(path + ".world", loc.getWorld().getName());
                config.set(path + ".x", loc.getX());
                config.set(path + ".y", loc.getY());
                config.set(path + ".z", loc.getZ());
                config.set(path + ".yaw", loc.getYaw());
                config.set(path + ".pitch", loc.getPitch());
            }
        }
        
        // Start region (new format: min/max volume)
        if (course.getStartRegion() != null) {
            DraftCourse.VolumeRegion region = course.getStartRegion();
            config.set("start.world", region.getWorld());
            if (region.getMin() != null) {
                config.set("start.min.x", region.getMin().getX());
                config.set("start.min.y", region.getMin().getY());
                config.set("start.min.z", region.getMin().getZ());
            }
            if (region.getMax() != null) {
                config.set("start.max.x", region.getMax().getX());
                config.set("start.max.y", region.getMax().getY());
                config.set("start.max.z", region.getMax().getZ());
            }
        }
        
        // Finish region (new format: min/max volume)
        if (course.getFinishRegion() != null) {
            DraftCourse.VolumeRegion region = course.getFinishRegion();
            config.set("finish.world", region.getWorld());
            if (region.getMin() != null) {
                config.set("finish.min.x", region.getMin().getX());
                config.set("finish.min.y", region.getMin().getY());
                config.set("finish.min.z", region.getMin().getZ());
            }
            if (region.getMax() != null) {
                config.set("finish.max.x", region.getMax().getX());
                config.set("finish.max.y", region.getMax().getY());
                config.set("finish.max.z", region.getMax().getZ());
            }
        }
        
        // Checkpoints
        List<CheckpointRegion> checkpoints = course.getCheckpoints();
        if (checkpoints != null && !checkpoints.isEmpty()) {
            for (int i = 0; i < checkpoints.size(); i++) {
                CheckpointRegion cp = checkpoints.get(i);
                String path = "checkpoints." + i;
                config.set(path + ".index", cp.getCheckpointIndex());
                if (cp.getPoint1() != null) {
                    config.set(path + ".point1.world", cp.getPoint1().getWorld());
                    config.set(path + ".point1.x", cp.getPoint1().getX());
                    config.set(path + ".point1.y", cp.getPoint1().getY());
                    config.set(path + ".point1.z", cp.getPoint1().getZ());
                }
                if (cp.getPoint2() != null) {
                    config.set(path + ".point2.world", cp.getPoint2().getWorld());
                    config.set(path + ".point2.x", cp.getPoint2().getX());
                    config.set(path + ".point2.y", cp.getPoint2().getY());
                    config.set(path + ".point2.z", cp.getPoint2().getZ());
                }
            }
        }
        
        config.save(file);
    }
    
    /**
     * Load a course from YAML file
     */
    public DraftCourse loadCourse(CourseType type, String courseName) throws IOException {
        String safeFileName = toSafeFileName(courseName);
        File file = getCourseFile(type, safeFileName);
        
        if (!file.exists()) {
            return null;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        DraftCourse course = new DraftCourse();
        
        // Basic info
        course.setName(config.getString("displayName", courseName));
        course.setType(CourseType.valueOf(config.getString("type", type.name())));
        course.setStatus(CourseStatus.valueOf(config.getString("status", "DRAFT")));
        
        // Course lobby spawn
        if (config.contains("courseLobby.world")) {
            String worldName = config.getString("courseLobby.world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                Location loc = new Location(
                    world,
                    config.getDouble("courseLobby.x"),
                    config.getDouble("courseLobby.y"),
                    config.getDouble("courseLobby.z"),
                    (float) config.getDouble("courseLobby.yaw"),
                    (float) config.getDouble("courseLobby.pitch")
                );
                course.setCourseLobbySpawn(loc);
            } else {
                plugin.getLogger().warning("Course '" + courseName + "': World '" + worldName + "' not loaded for course lobby spawn");
            }
        }
        
        // Player spawns
        if (config.contains("playerSpawns")) {
            for (String key : config.getConfigurationSection("playerSpawns").getKeys(false)) {
                String path = "playerSpawns." + key;
                String worldName = config.getString(path + ".world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(
                        world,
                        config.getDouble(path + ".x"),
                        config.getDouble(path + ".y"),
                        config.getDouble(path + ".z"),
                        (float) config.getDouble(path + ".yaw"),
                        (float) config.getDouble(path + ".pitch")
                    );
                    course.addPlayerSpawn(loc);
                } else {
                    plugin.getLogger().warning("Course '" + courseName + "': World '" + worldName + "' not loaded for player spawn #" + key);
                }
            }
        }
        
        // Start region (new format: min/max volume, with migration from old format)
        boolean startMigrated = false;
        if (config.contains("start.point1.world") && config.contains("start.point2.world")) {
            // Old format - migrate to new format
            BlockCoord p1 = new BlockCoord(
                config.getString("start.point1.world"),
                config.getInt("start.point1.x"),
                config.getInt("start.point1.y"),
                config.getInt("start.point1.z")
            );
            BlockCoord p2 = new BlockCoord(
                config.getString("start.point2.world"),
                config.getInt("start.point2.x"),
                config.getInt("start.point2.y"),
                config.getInt("start.point2.z")
            );
            // Calculate min/max from corners, Y bounds: minY = clicked Y, maxY = clicked Y + 1
            int minX = Math.min(p1.getX(), p2.getX());
            int maxX = Math.max(p1.getX(), p2.getX());
            int minY = Math.min(p1.getY(), p2.getY());
            int maxY = minY + 1; // Fixed height of 2 blocks
            int minZ = Math.min(p1.getZ(), p2.getZ());
            int maxZ = Math.max(p1.getZ(), p2.getZ());
            
            BlockCoord min = new BlockCoord(p1.getWorld(), minX, minY, minZ);
            BlockCoord max = new BlockCoord(p1.getWorld(), maxX, maxY, maxZ);
            course.setStartRegion(new DraftCourse.VolumeRegion(p1.getWorld(), min, max));
            startMigrated = true;
        } else if (config.contains("start.world") && config.contains("start.min.x") && config.contains("start.max.x")) {
            // New format
            String world = config.getString("start.world");
            BlockCoord min = new BlockCoord(
                world,
                config.getInt("start.min.x"),
                config.getInt("start.min.y"),
                config.getInt("start.min.z")
            );
            BlockCoord max = new BlockCoord(
                world,
                config.getInt("start.max.x"),
                config.getInt("start.max.y"),
                config.getInt("start.max.z")
            );
            course.setStartRegion(new DraftCourse.VolumeRegion(world, min, max));
        }
        
        // Finish region (new format: min/max volume, with migration from old format)
        boolean finishMigrated = false;
        if (config.contains("finish.point1.world") && config.contains("finish.point2.world")) {
            // Old format - migrate to new format
            BlockCoord p1 = new BlockCoord(
                config.getString("finish.point1.world"),
                config.getInt("finish.point1.x"),
                config.getInt("finish.point1.y"),
                config.getInt("finish.point1.z")
            );
            BlockCoord p2 = new BlockCoord(
                config.getString("finish.point2.world"),
                config.getInt("finish.point2.x"),
                config.getInt("finish.point2.y"),
                config.getInt("finish.point2.z")
            );
            // Calculate min/max from corners, Y bounds: minY = clicked Y, maxY = clicked Y + 1
            int minX = Math.min(p1.getX(), p2.getX());
            int maxX = Math.max(p1.getX(), p2.getX());
            int minY = Math.min(p1.getY(), p2.getY());
            int maxY = minY + 1; // Fixed height of 2 blocks
            int minZ = Math.min(p1.getZ(), p2.getZ());
            int maxZ = Math.max(p1.getZ(), p2.getZ());
            
            BlockCoord min = new BlockCoord(p1.getWorld(), minX, minY, minZ);
            BlockCoord max = new BlockCoord(p1.getWorld(), maxX, maxY, maxZ);
            course.setFinishRegion(new DraftCourse.VolumeRegion(p1.getWorld(), min, max));
            finishMigrated = true;
        } else if (config.contains("finish.world") && config.contains("finish.min.x") && config.contains("finish.max.x")) {
            // New format
            String world = config.getString("finish.world");
            BlockCoord min = new BlockCoord(
                world,
                config.getInt("finish.min.x"),
                config.getInt("finish.min.y"),
                config.getInt("finish.min.z")
            );
            BlockCoord max = new BlockCoord(
                world,
                config.getInt("finish.max.x"),
                config.getInt("finish.max.y"),
                config.getInt("finish.max.z")
            );
            course.setFinishRegion(new DraftCourse.VolumeRegion(world, min, max));
        }
        
        // Checkpoints
        if (config.contains("checkpoints")) {
            for (String key : config.getConfigurationSection("checkpoints").getKeys(false)) {
                String path = "checkpoints." + key;
                int index = config.getInt(path + ".index");
                BlockCoord p1 = null;
                BlockCoord p2 = null;
                
                if (config.contains(path + ".point1.world")) {
                    p1 = new BlockCoord(
                        config.getString(path + ".point1.world"),
                        config.getInt(path + ".point1.x"),
                        config.getInt(path + ".point1.y"),
                        config.getInt(path + ".point1.z")
                    );
                }
                if (config.contains(path + ".point2.world")) {
                    p2 = new BlockCoord(
                        config.getString(path + ".point2.world"),
                        config.getInt(path + ".point2.x"),
                        config.getInt(path + ".point2.y"),
                        config.getInt(path + ".point2.z")
                    );
                }
                
                if (p1 != null && p2 != null) {
                    course.addCheckpoint(new CheckpointRegion(index, p1, p2));
                }
            }
        }
        
        // Auto-save after migration (once, to prevent loops)
        if (startMigrated || finishMigrated) {
            try {
                saveCourse(course);
                plugin.getLogger().info("Course '" + courseName + "' migrated from old start/finish format and auto-saved");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to auto-save migrated course '" + courseName + "': " + e.getMessage());
            }
        }
        
        return course;
    }
    
    /**
     * Find course by name (searches both folders)
     */
    public DraftCourse findCourse(String courseName) {
        // Try boat racing first
        try {
            DraftCourse course = loadCourse(CourseType.BOAT, courseName);
            if (course != null) {
                return course;
            }
        } catch (IOException e) {
            // Ignore
        }
        
        // Try air racing
        try {
            return loadCourse(CourseType.AIR, courseName);
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Check if course exists
     */
    public boolean courseExists(String courseName) {
        String safeFileName = toSafeFileName(courseName);
        File boatFile = getCourseFile(CourseType.BOAT, safeFileName);
        File airFile = getCourseFile(CourseType.AIR, safeFileName);
        return boatFile.exists() || airFile.exists();
    }
    
    /**
     * List all course names (from both folders)
     */
    public List<String> listAllCourses() {
        List<String> courses = new ArrayList<>();
        
        // List boat racing courses
        File[] boatFiles = boatRacingFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (boatFiles != null) {
            for (File file : boatFiles) {
                try {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String displayName = config.getString("displayName");
                    if (displayName != null) {
                        courses.add(displayName);
                    }
                } catch (Exception e) {
                    // Skip invalid files
                }
            }
        }
        
        // List air racing courses
        File[] airFiles = airRacingFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (airFiles != null) {
            for (File file : airFiles) {
                try {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String displayName = config.getString("displayName");
                    if (displayName != null) {
                        courses.add(displayName);
                    }
                } catch (Exception e) {
                    // Skip invalid files
                }
            }
        }
        
        return courses;
    }
    
    /**
     * Delete a course
     */
    public boolean deleteCourse(String courseName) {
        String safeFileName = toSafeFileName(courseName);
        File boatFile = getCourseFile(CourseType.BOAT, safeFileName);
        File airFile = getCourseFile(CourseType.AIR, safeFileName);
        
        boolean deleted = false;
        if (boatFile.exists()) {
            deleted = boatFile.delete();
        }
        if (airFile.exists()) {
            deleted = airFile.delete() || deleted;
        }
        
        return deleted;
    }
}
