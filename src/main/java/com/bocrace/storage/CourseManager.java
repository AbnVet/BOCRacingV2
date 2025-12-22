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
    private File getCourseFile(CourseType type, String safeFileName) {
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
        
        // Start region
        if (course.getStartPoint1() != null) {
            BlockCoord p1 = course.getStartPoint1();
            config.set("start.point1.world", p1.getWorld());
            config.set("start.point1.x", p1.getX());
            config.set("start.point1.y", p1.getY());
            config.set("start.point1.z", p1.getZ());
        }
        if (course.getStartPoint2() != null) {
            BlockCoord p2 = course.getStartPoint2();
            config.set("start.point2.world", p2.getWorld());
            config.set("start.point2.x", p2.getX());
            config.set("start.point2.y", p2.getY());
            config.set("start.point2.z", p2.getZ());
        }
        
        // Finish region
        if (course.getFinishPoint1() != null) {
            BlockCoord p1 = course.getFinishPoint1();
            config.set("finish.point1.world", p1.getWorld());
            config.set("finish.point1.x", p1.getX());
            config.set("finish.point1.y", p1.getY());
            config.set("finish.point1.z", p1.getZ());
        }
        if (course.getFinishPoint2() != null) {
            BlockCoord p2 = course.getFinishPoint2();
            config.set("finish.point2.world", p2.getWorld());
            config.set("finish.point2.x", p2.getX());
            config.set("finish.point2.y", p2.getY());
            config.set("finish.point2.z", p2.getZ());
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
            World world = Bukkit.getWorld(config.getString("courseLobby.world"));
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
            }
        }
        
        // Player spawns
        if (config.contains("playerSpawns")) {
            for (String key : config.getConfigurationSection("playerSpawns").getKeys(false)) {
                String path = "playerSpawns." + key;
                World world = Bukkit.getWorld(config.getString(path + ".world"));
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
                }
            }
        }
        
        // Start region
        if (config.contains("start.point1.world")) {
            BlockCoord p1 = new BlockCoord(
                config.getString("start.point1.world"),
                config.getInt("start.point1.x"),
                config.getInt("start.point1.y"),
                config.getInt("start.point1.z")
            );
            course.setStartPoint1(p1);
        }
        if (config.contains("start.point2.world")) {
            BlockCoord p2 = new BlockCoord(
                config.getString("start.point2.world"),
                config.getInt("start.point2.x"),
                config.getInt("start.point2.y"),
                config.getInt("start.point2.z")
            );
            course.setStartPoint2(p2);
        }
        
        // Finish region
        if (config.contains("finish.point1.world")) {
            BlockCoord p1 = new BlockCoord(
                config.getString("finish.point1.world"),
                config.getInt("finish.point1.x"),
                config.getInt("finish.point1.y"),
                config.getInt("finish.point1.z")
            );
            course.setFinishPoint1(p1);
        }
        if (config.contains("finish.point2.world")) {
            BlockCoord p2 = new BlockCoord(
                config.getString("finish.point2.world"),
                config.getInt("finish.point2.x"),
                config.getInt("finish.point2.y"),
                config.getInt("finish.point2.z")
            );
            course.setFinishPoint2(p2);
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
