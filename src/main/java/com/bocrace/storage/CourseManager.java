package com.bocrace.storage;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.CourseType;
import com.bocrace.model.Course;
import com.bocrace.model.Course.BlockCoord;
import com.bocrace.model.Course.CheckpointRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.bocrace.model.Course.StartMode;
import com.bocrace.model.Course.CourseSettings;
import com.bocrace.model.Course.DropSettings;
import com.bocrace.model.Course.RulesSettings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bocrace.util.DebugLog;

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
     * Get course file name (for database storage)
     */
    public String getCourseFileName(CourseType type, String courseName) {
        String safeFileName = toSafeFileName(courseName);
        return safeFileName + ".yml";
    }
    
    /**
     * Save a course to YAML file
     */
    public void saveCourse(Course course) throws IOException {
        String safeFileName = toSafeFileName(course.getName());
        File file = getCourseFile(course.getType(), safeFileName);
        
        FileConfiguration config = new YamlConfiguration();
        
        // Basic info
        config.set("displayName", course.getName());
        config.set("fileName", safeFileName);
        config.set("type", course.getType().name());
        // Note: status field is no longer written (tolerated on load for compatibility)
        
        // Settings (with defaults)
        CourseSettings settings = course.getSettings();
        if (settings == null) {
            settings = new CourseSettings(); // Ensure defaults
        }
        config.set("settings.startMode", settings.getStartMode().name());
        config.set("settings.countdownSeconds", settings.getCountdownSeconds());
        config.set("settings.soloCooldownSeconds", settings.getSoloCooldownSeconds());
        config.set("settings.drop.shape", settings.getDrop().getShape().name());
        config.set("settings.drop.radius", settings.getDrop().getRadius());
        config.set("settings.drop.restoreSeconds", settings.getDrop().getRestoreSeconds());
        config.set("settings.rules.requireCheckpoints", settings.getRules().isRequireCheckpoints());
        
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
            Course.VolumeRegion region = course.getStartRegion();
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
            Course.VolumeRegion region = course.getFinishRegion();
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
        
        // SOLO buttons
        if (course.getSoloJoinButton() != null) {
            BlockCoord btn = course.getSoloJoinButton();
            config.set("soloJoinButton.world", btn.getWorld());
            config.set("soloJoinButton.x", btn.getX());
            config.set("soloJoinButton.y", btn.getY());
            config.set("soloJoinButton.z", btn.getZ());
        }
        if (course.getSoloReturnButton() != null) {
            BlockCoord btn = course.getSoloReturnButton();
            config.set("soloReturnButton.world", btn.getWorld());
            config.set("soloReturnButton.x", btn.getX());
            config.set("soloReturnButton.y", btn.getY());
            config.set("soloReturnButton.z", btn.getZ());
        }
        
        // MULTI items
        if (course.getMpLobby() != null) {
            Location loc = course.getMpLobby();
            config.set("mpLobby.world", loc.getWorld().getName());
            config.set("mpLobby.x", loc.getX());
            config.set("mpLobby.y", loc.getY());
            config.set("mpLobby.z", loc.getZ());
            config.set("mpLobby.yaw", loc.getYaw());
            config.set("mpLobby.pitch", loc.getPitch());
        }
        if (course.getMpJoinButton() != null) {
            BlockCoord btn = course.getMpJoinButton();
            config.set("mpJoinButton.world", btn.getWorld());
            config.set("mpJoinButton.x", btn.getX());
            config.set("mpJoinButton.y", btn.getY());
            config.set("mpJoinButton.z", btn.getZ());
        }
        if (course.getMpLeaderCreateButton() != null) {
            BlockCoord btn = course.getMpLeaderCreateButton();
            config.set("mpLeaderCreateButton.world", btn.getWorld());
            config.set("mpLeaderCreateButton.x", btn.getX());
            config.set("mpLeaderCreateButton.y", btn.getY());
            config.set("mpLeaderCreateButton.z", btn.getZ());
        }
        if (course.getMpLeaderStartButton() != null) {
            BlockCoord btn = course.getMpLeaderStartButton();
            config.set("mpLeaderStartButton.world", btn.getWorld());
            config.set("mpLeaderStartButton.x", btn.getX());
            config.set("mpLeaderStartButton.y", btn.getY());
            config.set("mpLeaderStartButton.z", btn.getZ());
        }
        if (course.getMpLeaderCancelButton() != null) {
            BlockCoord btn = course.getMpLeaderCancelButton();
            config.set("mpLeaderCancelButton.world", btn.getWorld());
            config.set("mpLeaderCancelButton.x", btn.getX());
            config.set("mpLeaderCancelButton.y", btn.getY());
            config.set("mpLeaderCancelButton.z", btn.getZ());
        }
        
        // Save with comments
        saveCourseWithComments(file, config, course.getType());
        
        // Debug log
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", course.getName());
        kv.put("displayName", course.getName());
        kv.put("file", file.getName());
        kv.put("type", course.getType().name());
        plugin.getDebugLog().info(DebugLog.Tag.DATA, "CourseManager", "Course saved", kv);
    }
    
    /**
     * Save course YAML with comment header
     */
    private void saveCourseWithComments(File file, FileConfiguration config, CourseType type) throws IOException {
        // Write comment header first
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("# BOCRacingV2 Course Configuration");
            writer.println("# Generated automatically - edit with care");
            writer.println();
            writer.println("# === START MODE SETTINGS ===");
            writer.println("# startMode options:");
            writer.println("#   CROSS_LINE = countdown runs; timer starts ONLY on crossing start trigger");
            writer.println("#                 (boat start volume / air start volume)");
            writer.println("#   DROP_START = countdown runs; timer starts at GO; blocks under racers drop briefly");
            writer.println("#");
            writer.println("# DROP_START shape options:");
            writer.println("#   SINGLE = drop only the block directly under the racer's spawn");
            writer.println("#   SQUARE = drop blocks in a square pattern (radius x radius) at spawn Y level");
            writer.println("#   CIRCLE = drop blocks in a circular pattern (radius) at spawn Y level");
            writer.println("# radius is only used for SQUARE and CIRCLE shapes");
            writer.println();
            writer.println("# === CHECKPOINTS ===");
            writer.println("# Checkpoints are optional unless settings.rules.requireCheckpoints=true");
            writer.println("# If required, you must have at least 1 checkpoint with sequential indexing");
            writer.println();
            writer.println("# === COURSE DATA ===");
            writer.println();
            
            // Write the actual YAML content
            String yamlContent = config.saveToString();
            writer.print(yamlContent);
        }
    }
    
    /**
     * Load a course from YAML file
     */
    public Course loadCourse(CourseType type, String courseName) throws IOException {
        String safeFileName = toSafeFileName(courseName);
        File file = getCourseFile(type, safeFileName);
        
        if (!file.exists()) {
            return null;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Course course = new Course();
        
        // Basic info
        course.setName(config.getString("displayName", courseName));
        course.setType(CourseType.valueOf(config.getString("type", type.name())));
        // Note: status field is tolerated on load for compatibility but not used
        
        // Debug log (before migration checks)
        Map<String, Object> kv = new HashMap<>();
        kv.put("course", courseName);
        kv.put("file", file.getName());
        kv.put("type", type.name());
        plugin.getDebugLog().info(DebugLog.Tag.DATA, "CourseManager", "Course loaded", kv);
        
        // Settings (with defaults if missing)
        CourseSettings settings = course.getSettings();
        if (config.contains("settings.startMode")) {
            try {
                settings.setStartMode(StartMode.valueOf(config.getString("settings.startMode")));
            } catch (IllegalArgumentException e) {
                settings.setStartMode(StartMode.CROSS_LINE); // Default
            }
        }
        settings.setCountdownSeconds(config.getInt("settings.countdownSeconds", 5));
        settings.setSoloCooldownSeconds(config.getInt("settings.soloCooldownSeconds", 120));
        if (config.contains("settings.drop.shape")) {
            try {
                settings.getDrop().setShape(Course.DropSettings.DropShape.valueOf(config.getString("settings.drop.shape")));
            } catch (IllegalArgumentException e) {
                settings.getDrop().setShape(Course.DropSettings.DropShape.SINGLE); // Default
            }
        }
        if (config.contains("settings.drop.radius")) {
            settings.getDrop().setRadius(config.getInt("settings.drop.radius", 1));
        }
        if (config.contains("settings.drop.restoreSeconds")) {
            settings.getDrop().setRestoreSeconds(config.getInt("settings.drop.restoreSeconds", 10));
        }
        if (config.contains("settings.rules.requireCheckpoints")) {
            settings.getRules().setRequireCheckpoints(config.getBoolean("settings.rules.requireCheckpoints", false));
        }
        
        // If settings were missing, save back with defaults
        boolean needsSave = !config.contains("settings.startMode");
        
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
            course.setStartRegion(new Course.VolumeRegion(p1.getWorld(), min, max));
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
            course.setStartRegion(new Course.VolumeRegion(world, min, max));
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
            course.setFinishRegion(new Course.VolumeRegion(p1.getWorld(), min, max));
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
            course.setFinishRegion(new Course.VolumeRegion(world, min, max));
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
        
        // SOLO buttons
        if (config.contains("soloJoinButton.world")) {
            BlockCoord btn = new BlockCoord(
                config.getString("soloJoinButton.world"),
                config.getInt("soloJoinButton.x"),
                config.getInt("soloJoinButton.y"),
                config.getInt("soloJoinButton.z")
            );
            course.setSoloJoinButton(btn);
        }
        if (config.contains("soloReturnButton.world")) {
            BlockCoord btn = new BlockCoord(
                config.getString("soloReturnButton.world"),
                config.getInt("soloReturnButton.x"),
                config.getInt("soloReturnButton.y"),
                config.getInt("soloReturnButton.z")
            );
            course.setSoloReturnButton(btn);
        }
        
        // MULTI items
        if (config.contains("mpLobby.world")) {
            World world = Bukkit.getWorld(config.getString("mpLobby.world"));
            if (world != null) {
                Location loc = new Location(
                    world,
                    config.getDouble("mpLobby.x"),
                    config.getDouble("mpLobby.y"),
                    config.getDouble("mpLobby.z"),
                    (float) config.getDouble("mpLobby.yaw"),
                    (float) config.getDouble("mpLobby.pitch")
                );
                course.setMpLobby(loc);
            }
        }
        if (config.contains("mpJoinButton.world")) {
            BlockCoord btn = new BlockCoord(
                config.getString("mpJoinButton.world"),
                config.getInt("mpJoinButton.x"),
                config.getInt("mpJoinButton.y"),
                config.getInt("mpJoinButton.z")
            );
            course.setMpJoinButton(btn);
        }
        if (config.contains("mpLeaderCreateButton.world")) {
            BlockCoord btn = new BlockCoord(
                config.getString("mpLeaderCreateButton.world"),
                config.getInt("mpLeaderCreateButton.x"),
                config.getInt("mpLeaderCreateButton.y"),
                config.getInt("mpLeaderCreateButton.z")
            );
            course.setMpLeaderCreateButton(btn);
        }
        if (config.contains("mpLeaderStartButton.world")) {
            BlockCoord btn = new BlockCoord(
                config.getString("mpLeaderStartButton.world"),
                config.getInt("mpLeaderStartButton.x"),
                config.getInt("mpLeaderStartButton.y"),
                config.getInt("mpLeaderStartButton.z")
            );
            course.setMpLeaderStartButton(btn);
        }
        if (config.contains("mpLeaderCancelButton.world")) {
            BlockCoord btn = new BlockCoord(
                config.getString("mpLeaderCancelButton.world"),
                config.getInt("mpLeaderCancelButton.x"),
                config.getInt("mpLeaderCancelButton.y"),
                config.getInt("mpLeaderCancelButton.z")
            );
            course.setMpLeaderCancelButton(btn);
        }
        
        // Auto-save after migration or if settings were missing (once, to prevent loops)
        if (startMigrated || finishMigrated || needsSave) {
                    try {
                            saveCourse(course);
                            if (startMigrated || finishMigrated) {
                                plugin.getLogger().info("Course '" + courseName + "' migrated from old start/finish format and auto-saved");
                                // Debug log migration
                                Map<String, Object> migrateKv = new HashMap<>();
                                migrateKv.put("course", courseName);
                                migrateKv.put("file", file.getName());
                                migrateKv.put("oldFormat", "point1/point2");
                                migrateKv.put("newFormat", "min/max");
                                plugin.getDebugLog().info(DebugLog.Tag.DATA, "CourseManager", "Course migrated (start/finish)", migrateKv);
                            }
                            if (needsSave) {
                                plugin.getLogger().info("Course '" + courseName + "' settings defaults applied and auto-saved");
                                // Debug log settings migration
                                Map<String, Object> settingsKv = new HashMap<>();
                                settingsKv.put("course", courseName);
                                settingsKv.put("file", file.getName());
                                settingsKv.put("oldFormat", "missing");
                                settingsKv.put("newFormat", "defaults");
                                plugin.getDebugLog().info(DebugLog.Tag.DATA, "CourseManager", "Course migrated (settings)", settingsKv);
                            }
                        } catch (IOException e) {
                            plugin.getLogger().warning("Failed to auto-save course '" + courseName + "': " + e.getMessage());
                        }
                    }
        
        return course;
    }
    
    /**
     * Find course by name (searches both folders)
     */
    public Course findCourse(String courseName) {
        // Try boat racing first
        try {
            Course course = loadCourse(CourseType.BOAT, courseName);
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
