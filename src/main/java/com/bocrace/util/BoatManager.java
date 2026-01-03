package com.bocrace.util;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.Course;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages race boat spawning, tracking, and cleanup
 */
public class BoatManager {
    
    private final BOCRacingV2 plugin;
    private final PDCKeys pdcKeys;
    
    public BoatManager(BOCRacingV2 plugin) {
        this.plugin = plugin;
        this.pdcKeys = new PDCKeys(plugin);
    }
    
    /**
     * Spawn a race boat for a player at the specified spawn location
     */
    public Boat spawnRaceBoat(Player player, Location spawnLocation, Course course, String runId) {
        if (spawnLocation == null) {
            plugin.getLogger().warning("Cannot spawn boat - no spawn location provided");
            return null;
        }
        
        // Use exact spawn location including yaw/pitch for proper direction
        Location boatSpawn = spawnLocation.clone();
        boatSpawn.setX(spawnLocation.getBlockX() + 0.5); // Center X
        boatSpawn.setZ(spawnLocation.getBlockZ() + 0.5); // Center Z
        boatSpawn.add(0, 1.0, 0); // Add Y offset
        // Keep original yaw/pitch from setup
        boatSpawn.setYaw(spawnLocation.getYaw());
        boatSpawn.setPitch(spawnLocation.getPitch());
        
        // Spawn the boat with course-specific type
        EntityType boatType = parseBoatType(course.getBoatType());
        Boat boat = (Boat) boatSpawn.getWorld().spawnEntity(boatSpawn, boatType);
        
        // Tag the boat with PDC data
        boat.getPersistentDataContainer().set(pdcKeys.raceBoat, PersistentDataType.BOOLEAN, true);
        boat.getPersistentDataContainer().set(pdcKeys.playerUuid, PersistentDataType.STRING, player.getUniqueId().toString());
        boat.getPersistentDataContainer().set(pdcKeys.courseName, PersistentDataType.STRING, course.getName());
        if (runId != null) {
            boat.getPersistentDataContainer().set(pdcKeys.runId, PersistentDataType.STRING, runId);
        }
        
        // Teleport player to boat location and add as passenger
        player.teleport(boatSpawn);
        boat.addPassenger(player);
        
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "BoatManager", "Boat spawned", 
            Map.of("player", player.getName(), "course", course.getName(), "boatType", boatType.name(), "runId", runId != null ? runId : "null"));
        
        return boat;
    }
    
    /**
     * Check if an entity is a race boat
     */
    public boolean isRaceBoat(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof Boat)) return false;
        return entity.getPersistentDataContainer().has(pdcKeys.raceBoat, PersistentDataType.BOOLEAN);
    }
    
    /**
     * Get the player UUID associated with a race boat
     */
    public UUID getRaceBoatPlayer(Boat boat) {
        if (!isRaceBoat(boat)) return null;
        
        String uuidString = boat.getPersistentDataContainer().get(pdcKeys.playerUuid, PersistentDataType.STRING);
        if (uuidString == null) return null;
        
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID in boat PDC: " + uuidString);
            return null;
        }
    }
    
    /**
     * Remove a race boat and cleanup
     */
    public void removeRaceBoat(Boat boat, String reason) {
        if (boat == null || boat.isDead()) return;
        
        UUID playerUuid = getRaceBoatPlayer(boat);
        String courseName = boat.getPersistentDataContainer().get(pdcKeys.courseName, PersistentDataType.STRING);
        
        plugin.getDebugLog().info(DebugLog.Tag.STATE, "BoatManager", "Removing race boat", 
            Map.of("reason", reason, "course", courseName != null ? courseName : "unknown"));
        
        // Remove all passengers
        boat.getPassengers().forEach(boat::removePassenger);
        
        // Remove the boat
        boat.remove();
    }
    
    /**
     * Find a race boat by player UUID
     */
    public Boat findRaceBoatByPlayer(UUID playerUuid) {
        // Search all loaded worlds
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof Boat && isRaceBoat((Boat) entity)) {
                    Boat boat = (Boat) entity;
                    UUID boatPlayer = getRaceBoatPlayer(boat);
                    if (playerUuid.equals(boatPlayer)) {
                        return boat;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Cleanup all race boats (for plugin disable/reload)
     */
    public int cleanupAllRaceBoats() {
        int count = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof Boat && isRaceBoat((Boat) entity)) {
                    removeRaceBoat((Boat) entity, "plugin_cleanup");
                    count++;
                }
            }
        }
        plugin.getLogger().info("Cleaned up " + count + " race boats");
        return count;
    }
    
    /**
     * Parse boat type string to EntityType with fallback to OAK_BOAT
     */
    private EntityType parseBoatType(String boatTypeName) {
        if (boatTypeName == null || boatTypeName.trim().isEmpty()) {
            return EntityType.OAK_BOAT; // Default
        }
        
        try {
            // Try to parse the boat type
            String normalizedName = boatTypeName.toUpperCase().trim();
            
            // Add _BOAT suffix if not present
            if (!normalizedName.endsWith("_BOAT") && !normalizedName.endsWith("_RAFT")) {
                normalizedName = normalizedName + "_BOAT";
            }
            
            // Parse the EntityType
            EntityType boatType = EntityType.valueOf(normalizedName);
            
            // Verify it's actually a boat type
            if (isValidBoatType(boatType)) {
                return boatType;
            } else {
                plugin.getLogger().warning("Invalid boat type: " + boatTypeName + " - using OAK_BOAT");
                return EntityType.OAK_BOAT;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown boat type: " + boatTypeName + " - using OAK_BOAT");
            return EntityType.OAK_BOAT;
        }
    }
    
    /**
     * Check if an EntityType is a valid boat type
     */
    private boolean isValidBoatType(EntityType type) {
        switch (type) {
            case OAK_BOAT:
            case BIRCH_BOAT:
            case SPRUCE_BOAT:
            case JUNGLE_BOAT:
            case ACACIA_BOAT:
            case DARK_OAK_BOAT:
            case MANGROVE_BOAT:
            case CHERRY_BOAT:
            case BAMBOO_RAFT:
            case PALE_OAK_BOAT:
                return true;
            default:
                return false;
        }
    }
}
