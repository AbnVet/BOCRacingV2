package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import com.bocrace.model.Course.BlockCoord;
import com.bocrace.model.Course.CheckpointRegion;
import com.bocrace.setup.SetupSession;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.CourseManager;
import com.bocrace.util.SetupFeedback;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.IOException;

/**
 * Handles right-click capture for admin setup
 */
public class SetupListener implements Listener {
    
    private final BOCRacingV2 plugin;
    private final SetupSessionManager sessionManager;
    private final CourseManager courseManager;
    
    public SetupListener(BOCRacingV2 plugin, SetupSessionManager sessionManager, CourseManager courseManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.courseManager = courseManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Only handle right-click on blocks, main hand
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        
        // Check if player has armed action
        if (!sessionManager.hasSession(player)) {
            return;
        }
        
        SetupSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }
        
        // Cancel event to prevent block interaction
        event.setCancelled(true);
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        
        Location blockLoc = clickedBlock.getLocation();
        String worldName = blockLoc.getWorld().getName();
        
        // Load course
        Course course = courseManager.findCourse(session.getCourseName());
        if (course == null) {
            player.sendMessage("§cCourse not found!");
            sessionManager.clearSession(player);
            return;
        }
        
        // Handle based on armed action
        boolean success = false;
        boolean shouldClearSession = false;
        
        switch (session.getArmedAction()) {
            case PLAYER_SPAWN:
                success = handlePlayerSpawn(player, course, blockLoc);
                shouldClearSession = true;
                break;
                
            case COURSE_LOBBY:
                success = handleCourseLobby(player, course, blockLoc);
                shouldClearSession = true;
                break;
                
            case START:
                success = handleStartRegion(player, session, course, blockLoc, worldName);
                break;
                
            case FINISH:
                success = handleFinishRegion(player, session, course, blockLoc, worldName);
                break;
                
            case CHECKPOINT:
                success = handleCheckpointRegion(player, session, course, blockLoc, worldName);
                break;
        }
        
        if (success) {
            // Save course
            try {
                courseManager.saveCourse(course);
            } catch (IOException e) {
                player.sendMessage("§cFailed to save course: " + e.getMessage());
                plugin.getLogger().severe("Failed to save course: " + e.getMessage());
                return;
            }
            
            // Clear session if single-click action
            if (shouldClearSession) {
                sessionManager.clearSession(player);
            }
        }
    }
    
    private boolean handlePlayerSpawn(Player player, Course draft, Location blockLoc) {
        // Center of block, above block, with yaw from player view, pitch=0
        Location spawnLoc = new Location(
            blockLoc.getWorld(),
            blockLoc.getBlockX() + 0.5,
            blockLoc.getBlockY() + 1.0,
            blockLoc.getBlockZ() + 0.5,
            player.getLocation().getYaw(),
            0.0f // Force pitch = 0
        );
        
        draft.addPlayerSpawn(spawnLoc);
        int spawnNumber = draft.getPlayerSpawns().size();
        
        // Send feedback
        SetupFeedback.sendSingleClickFeedback(player, draft.getName(), "Player Spawn #" + spawnNumber, spawnLoc);
        
        return true;
    }
    
    private boolean handleCourseLobby(Player player, Course draft, Location blockLoc) {
        // Center of block, above block, with yaw from player view, pitch=0
        Location lobbyLoc = new Location(
            blockLoc.getWorld(),
            blockLoc.getBlockX() + 0.5,
            blockLoc.getBlockY() + 1.0,
            blockLoc.getBlockZ() + 0.5,
            player.getLocation().getYaw(),
            0.0f // Force pitch = 0
        );
        
        draft.setCourseLobbySpawn(lobbyLoc);
        
        // Send feedback
        SetupFeedback.sendSingleClickFeedback(player, draft.getName(), "Course Lobby", lobbyLoc);
        
        return true;
    }
    
    private boolean handleStartRegion(Player player, SetupSession session, Course draft, Location blockLoc, String worldName) {
        BlockCoord corner = new BlockCoord(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        
        if (session.getPendingPoint1() == null) {
            // First click - corner A
            session.setPendingPoint1(corner);
            
            // Send feedback
            SetupFeedback.sendVolumeCornerAFeedback(player, draft.getName(), "Start", blockLoc);
            
            return true;
        } else {
            // Second click - corner B, calculate volume bounds
            BlockCoord cornerA = session.getPendingPoint1();
            BlockCoord cornerB = corner;
            
            // Calculate min/max from corners
            int minX = Math.min(cornerA.getX(), cornerB.getX());
            int maxX = Math.max(cornerA.getX(), cornerB.getX());
            int minY = Math.min(cornerA.getY(), cornerB.getY());
            int maxY = minY + 1; // Fixed height of 2 blocks
            int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
            int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
            
            BlockCoord min = new BlockCoord(worldName, minX, minY, minZ);
            BlockCoord max = new BlockCoord(worldName, maxX, maxY, maxZ);
            Course.VolumeRegion volume = new Course.VolumeRegion(worldName, min, max);
            draft.setStartRegion(volume);
            
            session.clearPendingPoint1();
            sessionManager.clearSession(player);
            
            // Send feedback
            SetupFeedback.sendVolumeCompleteFeedback(player, draft.getName(), "Start", 
                minX, minY, minZ, maxX, maxY, maxZ);
            
            return true;
        }
    }
    
    private boolean handleFinishRegion(Player player, SetupSession session, Course draft, Location blockLoc, String worldName) {
        BlockCoord corner = new BlockCoord(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        
        if (session.getPendingPoint1() == null) {
            // First click - corner A
            session.setPendingPoint1(corner);
            
            // Send feedback
            SetupFeedback.sendVolumeCornerAFeedback(player, draft.getName(), "Finish", blockLoc);
            
            return true;
        } else {
            // Second click - corner B, calculate volume bounds
            BlockCoord cornerA = session.getPendingPoint1();
            BlockCoord cornerB = corner;
            
            // Calculate min/max from corners
            int minX = Math.min(cornerA.getX(), cornerB.getX());
            int maxX = Math.max(cornerA.getX(), cornerB.getX());
            int minY = Math.min(cornerA.getY(), cornerB.getY());
            int maxY = minY + 1; // Fixed height of 2 blocks
            int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
            int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
            
            BlockCoord min = new BlockCoord(worldName, minX, minY, minZ);
            BlockCoord max = new BlockCoord(worldName, maxX, maxY, maxZ);
            Course.VolumeRegion volume = new Course.VolumeRegion(worldName, min, max);
            draft.setFinishRegion(volume);
            
            session.clearPendingPoint1();
            sessionManager.clearSession(player);
            
            // Send feedback
            SetupFeedback.sendVolumeCompleteFeedback(player, draft.getName(), "Finish", 
                minX, minY, minZ, maxX, maxY, maxZ);
            
            return true;
        }
    }
    
    private boolean handleCheckpointRegion(Player player, SetupSession session, Course draft, Location blockLoc, String worldName) {
        BlockCoord point = new BlockCoord(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        
        if (session.getPendingPoint1() == null) {
            // First click - set point1
            session.setPendingPoint1(point);
            int checkpointIndex = draft.getCheckpoints().size() + 1;
            
            // Send feedback
            SetupFeedback.sendCheckpointPoint1Feedback(player, draft.getName(), checkpointIndex, blockLoc);
            
            return true;
        } else {
            // Second click - set point2 and complete checkpoint
            int checkpointIndex = draft.getCheckpoints().size() + 1;
            CheckpointRegion checkpoint = new CheckpointRegion(checkpointIndex, session.getPendingPoint1(), point);
            draft.addCheckpoint(checkpoint);
            session.clearPendingPoint1();
            sessionManager.clearSession(player);
            
            // Send feedback
            SetupFeedback.sendCheckpointCompleteFeedback(player, draft.getName(), checkpointIndex, blockLoc);
            
            return true;
        }
    }
}
