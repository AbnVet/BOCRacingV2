package com.bocrace.listener;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.DraftCourse;
import com.bocrace.model.DraftCourse.BlockCoord;
import com.bocrace.model.DraftCourse.CheckpointRegion;
import com.bocrace.setup.SetupSession;
import com.bocrace.setup.SetupSessionManager;
import com.bocrace.storage.DraftManager;
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
    private final DraftManager draftManager;
    
    public SetupListener(BOCRacingV2 plugin, SetupSessionManager sessionManager, DraftManager draftManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.draftManager = draftManager;
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
        
        // Load draft
        DraftCourse draft;
        try {
            draft = draftManager.loadDraft(session.getCourseName());
            if (draft == null) {
                player.sendMessage("§cDraft course not found!");
                sessionManager.clearSession(player);
                return;
            }
        } catch (IOException e) {
            player.sendMessage("§cFailed to load draft: " + e.getMessage());
            sessionManager.clearSession(player);
            return;
        }
        
        // Handle based on armed action
        boolean success = false;
        boolean shouldClearSession = false;
        
        switch (session.getArmedAction()) {
            case PLAYER_SPAWN:
                success = handlePlayerSpawn(player, draft, blockLoc);
                shouldClearSession = true;
                break;
                
            case COURSE_LOBBY:
                success = handleCourseLobby(player, draft, blockLoc);
                shouldClearSession = true;
                break;
                
            case START:
                success = handleStartRegion(player, session, draft, blockLoc, worldName);
                break;
                
            case FINISH:
                success = handleFinishRegion(player, session, draft, blockLoc, worldName);
                break;
                
            case CHECKPOINT:
                success = handleCheckpointRegion(player, session, draft, blockLoc, worldName);
                break;
        }
        
        if (success) {
            // Save draft
            try {
                draftManager.saveDraft(draft);
                player.sendMessage("§aLocation saved!");
            } catch (IOException e) {
                player.sendMessage("§cFailed to save draft: " + e.getMessage());
                plugin.getLogger().severe("Failed to save draft: " + e.getMessage());
            }
            
            // Clear session if single-click action
            if (shouldClearSession) {
                sessionManager.clearSession(player);
            }
        }
    }
    
    private boolean handlePlayerSpawn(Player player, DraftCourse draft, Location blockLoc) {
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
        player.sendMessage("§aPlayer spawn #" + spawnNumber + " set!");
        return true;
    }
    
    private boolean handleCourseLobby(Player player, DraftCourse draft, Location blockLoc) {
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
        player.sendMessage("§aCourse lobby spawn set!");
        return true;
    }
    
    private boolean handleStartRegion(Player player, SetupSession session, DraftCourse draft, Location blockLoc, String worldName) {
        BlockCoord point = new BlockCoord(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        
        if (session.getPendingPoint1() == null) {
            // First click - set point1
            session.setPendingPoint1(point);
            draft.setStartPoint1(point);
            player.sendMessage("§aStart line point 1 set!");
            player.sendMessage("§7Right-click block 2 of 2 to complete start line");
            return true;
        } else {
            // Second click - set point2 and complete
            draft.setStartPoint2(point);
            session.clearPendingPoint1();
            sessionManager.clearSession(player);
            player.sendMessage("§aStart line point 2 set! Start region complete.");
            return true;
        }
    }
    
    private boolean handleFinishRegion(Player player, SetupSession session, DraftCourse draft, Location blockLoc, String worldName) {
        BlockCoord point = new BlockCoord(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        
        if (session.getPendingPoint1() == null) {
            // First click - set point1
            session.setPendingPoint1(point);
            draft.setFinishPoint1(point);
            player.sendMessage("§aFinish line point 1 set!");
            player.sendMessage("§7Right-click block 2 of 2 to complete finish line");
            return true;
        } else {
            // Second click - set point2 and complete
            draft.setFinishPoint2(point);
            session.clearPendingPoint1();
            sessionManager.clearSession(player);
            player.sendMessage("§aFinish line point 2 set! Finish region complete.");
            return true;
        }
    }
    
    private boolean handleCheckpointRegion(Player player, SetupSession session, DraftCourse draft, Location blockLoc, String worldName) {
        BlockCoord point = new BlockCoord(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        
        if (session.getPendingPoint1() == null) {
            // First click - set point1
            session.setPendingPoint1(point);
            int checkpointIndex = draft.getCheckpoints().size() + 1;
            player.sendMessage("§aCheckpoint #" + checkpointIndex + " point 1 set!");
            player.sendMessage("§7Right-click block 2 of 2 to complete checkpoint");
            return true;
        } else {
            // Second click - set point2 and complete checkpoint
            int checkpointIndex = draft.getCheckpoints().size() + 1;
            CheckpointRegion checkpoint = new CheckpointRegion(checkpointIndex, session.getPendingPoint1(), point);
            draft.addCheckpoint(checkpoint);
            session.clearPendingPoint1();
            sessionManager.clearSession(player);
            player.sendMessage("§aCheckpoint #" + checkpointIndex + " point 2 set! Checkpoint complete.");
            return true;
        }
    }
}
