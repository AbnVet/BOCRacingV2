package com.bocrace.runtime;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.util.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Manages block drops and restoration for DROP_START mode
 */
public class DropBlockManager {
    
    /**
     * Stores block states to restore
     */
    public static class DropTask {
        private final String worldName;
        private final Map<Location, BlockState> blocksToRestore;
        private final int taskId;
        
        public DropTask(String worldName, Map<Location, BlockState> blocksToRestore, int taskId) {
            this.worldName = worldName;
            this.blocksToRestore = new HashMap<>(blocksToRestore);
            this.taskId = taskId;
        }
        
        public String getWorldName() {
            return worldName;
        }
        
        public Map<Location, BlockState> getBlocksToRestore() {
            return blocksToRestore;
        }
        
        public int getTaskId() {
            return taskId;
        }
    }
    
    private final Plugin plugin;
    private final Map<RaceManager.CourseKey, List<DropTask>> activeDropTasks;
    
    public DropBlockManager(Plugin plugin) {
        this.plugin = plugin;
        this.activeDropTasks = new HashMap<>();
    }
    
    /**
     * Drop blocks under a racer's spawn location based on drop settings
     */
    public void dropBlocks(RaceManager.CourseKey courseKey, Location spawnLoc, Course.DropSettings dropSettings) {
        World world = spawnLoc.getWorld();
        if (world == null) return;
        
        int centerX = spawnLoc.getBlockX();
        int centerY = spawnLoc.getBlockY() - 1; // Block directly under spawn
        int centerZ = spawnLoc.getBlockZ();
        
        Map<Location, BlockState> blocksToRestore = new HashMap<>();
        
        switch (dropSettings.getShape()) {
            case SINGLE:
                Block singleBlock = world.getBlockAt(centerX, centerY, centerZ);
                blocksToRestore.put(singleBlock.getLocation(), singleBlock.getState());
                singleBlock.setType(Material.AIR);
                break;
                
            case SQUARE:
                int radius = dropSettings.getRadius();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int x = centerX + dx;
                        int z = centerZ + dz;
                        Block block = world.getBlockAt(x, centerY, z);
                        blocksToRestore.put(block.getLocation(), block.getState());
                        block.setType(Material.AIR);
                    }
                }
                break;
                
            case CIRCLE:
                int circleRadius = dropSettings.getRadius();
                int radiusSquared = circleRadius * circleRadius;
                for (int dx = -circleRadius; dx <= circleRadius; dx++) {
                    for (int dz = -circleRadius; dz <= circleRadius; dz++) {
                        if (dx * dx + dz * dz <= radiusSquared) {
                            int x = centerX + dx;
                            int z = centerZ + dz;
                            Block block = world.getBlockAt(x, centerY, z);
                            blocksToRestore.put(block.getLocation(), block.getState());
                            block.setType(Material.AIR);
                        }
                    }
                }
                break;
        }
        
        // Schedule restoration
        int restoreTicks = dropSettings.getRestoreSeconds() * 20; // Convert seconds to ticks
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restoreBlocks(courseKey, blocksToRestore);
        }, restoreTicks).getTaskId();
        
        // Store the task
        DropTask task = new DropTask(world.getName(), blocksToRestore, taskId);
        activeDropTasks.computeIfAbsent(courseKey, k -> new ArrayList<>()).add(task);
        
        // Debug log (if plugin is BOCRacingV2 instance)
        if (plugin instanceof BOCRacingV2) {
            Map<String, Object> kv = new HashMap<>();
            kv.put("course", courseKey.getName());
            kv.put("shape", dropSettings.getShape().name());
            kv.put("radius", dropSettings.getRadius());
            kv.put("restoreSeconds", dropSettings.getRestoreSeconds());
            ((BOCRacingV2) plugin).getDebugLog().info(DebugLog.Tag.STATE, "DropBlockManager", "DROP_EXEC", kv);
        }
    }
    
    /**
     * Restore blocks immediately
     */
    private void restoreBlocks(RaceManager.CourseKey courseKey, Map<Location, BlockState> blocksToRestore) {
        for (Map.Entry<Location, BlockState> entry : blocksToRestore.entrySet()) {
            Location loc = entry.getKey();
            BlockState state = entry.getValue();
            if (state == null) continue;
            
            World world = state.getWorld();
            if (world == null) {
                // Try to get world by name from location
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : null;
                if (worldName == null) continue;
                world = Bukkit.getWorld(worldName);
            }
            
            if (world != null) {
                Block block = world.getBlockAt(loc);
                // Restore using BlockState
                state.update(true, false);
            }
        }
        
        // Debug log restore (if plugin is BOCRacingV2 instance)
        if (plugin instanceof BOCRacingV2) {
            Map<String, Object> kv = new HashMap<>();
            kv.put("course", courseKey.getName());
            ((BOCRacingV2) plugin).getDebugLog().info(DebugLog.Tag.STATE, "DropBlockManager", "DROP_RESTORE", kv);
        }
        
        // Remove from active tasks
        List<DropTask> tasks = activeDropTasks.get(courseKey);
        if (tasks != null) {
            tasks.removeIf(task -> task.getBlocksToRestore().equals(blocksToRestore));
            if (tasks.isEmpty()) {
                activeDropTasks.remove(courseKey);
            }
        }
    }
    
    /**
     * Cancel and restore all blocks for a course
     */
    public void cancelAllDrops(RaceManager.CourseKey courseKey) {
        List<DropTask> tasks = activeDropTasks.remove(courseKey);
        if (tasks == null) return;
        
        for (DropTask task : tasks) {
            // Cancel the scheduled task
            Bukkit.getScheduler().cancelTask(task.getTaskId());
            // Restore blocks immediately
            restoreBlocks(courseKey, task.getBlocksToRestore());
        }
    }
    
    /**
     * Clear all (on plugin disable)
     */
    public void clearAll() {
        for (RaceManager.CourseKey key : new ArrayList<>(activeDropTasks.keySet())) {
            cancelAllDrops(key);
        }
        activeDropTasks.clear();
    }
}
