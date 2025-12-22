package com.bocrace.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Provides visual and audio feedback for setup captures
 */
public class SetupFeedback {
    
    /**
     * Send feedback for a single-click capture (spawn/lobby)
     */
    public static void sendSingleClickFeedback(Player player, String courseName, String actionName, Location location) {
        // Sound
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        
        // ActionBar message
        String coords = String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
        String yaw = String.format("%.0f°", location.getYaw());
        Component message = Component.text()
            .append(Component.text("✓ ", NamedTextColor.GREEN))
            .append(Component.text(courseName, NamedTextColor.YELLOW))
            .append(Component.text(" - ", NamedTextColor.GRAY))
            .append(Component.text(actionName, NamedTextColor.AQUA))
            .append(Component.text(" saved: ", NamedTextColor.GRAY))
            .append(Component.text(coords, NamedTextColor.WHITE))
            .append(Component.text(" (yaw: ", NamedTextColor.GRAY))
            .append(Component.text(yaw, NamedTextColor.WHITE))
            .append(Component.text(")", NamedTextColor.GRAY))
            .build();
        player.sendActionBar(message);
        
        // Particles
        Location particleLoc = location.clone().add(0, 0.5, 0);
        player.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            particleLoc,
            8, 0.3, 0.3, 0.3, 0.01
        );
    }
    
    /**
     * Send feedback for region point 1 capture
     */
    public static void sendRegionPoint1Feedback(Player player, String courseName, String regionName, Location blockLoc) {
        // Sound
        player.playSound(blockLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.1f);
        
        // ActionBar message
        String coords = String.format("%d, %d, %d", blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        Component message = Component.text()
            .append(Component.text("✓ ", NamedTextColor.GREEN))
            .append(Component.text(courseName, NamedTextColor.YELLOW))
            .append(Component.text(" - ", NamedTextColor.GRAY))
            .append(Component.text(regionName, NamedTextColor.AQUA))
            .append(Component.text(" Point 1 saved: ", NamedTextColor.GRAY))
            .append(Component.text(coords, NamedTextColor.WHITE))
            .append(Component.text(" (Click Point 2)", NamedTextColor.GRAY))
            .build();
        player.sendActionBar(message);
        
        // Particles
        Location particleLoc = blockLoc.clone().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            particleLoc,
            6, 0.2, 0.2, 0.2, 0.01
        );
    }
    
    /**
     * Send feedback for region point 2 capture (region complete)
     */
    public static void sendRegionCompleteFeedback(Player player, String courseName, String regionName, Location blockLoc) {
        // Sound (slightly higher pitch for completion)
        player.playSound(blockLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        
        // ActionBar message
        String coords = String.format("%d, %d, %d", blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        Component message = Component.text()
            .append(Component.text("✓✓ ", NamedTextColor.GREEN))
            .append(Component.text(courseName, NamedTextColor.YELLOW))
            .append(Component.text(" - ", NamedTextColor.GRAY))
            .append(Component.text(regionName, NamedTextColor.AQUA))
            .append(Component.text(" Point 2 saved: ", NamedTextColor.GRAY))
            .append(Component.text(coords, NamedTextColor.WHITE))
            .append(Component.text(" (Region complete!)", NamedTextColor.GREEN))
            .build();
        player.sendActionBar(message);
        
        // Particles (more for completion)
        Location particleLoc = blockLoc.clone().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(
            Particle.FIREWORK,
            particleLoc,
            12, 0.3, 0.3, 0.3, 0.02
        );
    }
    
    /**
     * Send feedback for checkpoint point 1 capture
     */
    public static void sendCheckpointPoint1Feedback(Player player, String courseName, int checkpointIndex, Location blockLoc) {
        // Sound
        player.playSound(blockLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.1f);
        
        // ActionBar message
        String coords = String.format("%d, %d, %d", blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        Component message = Component.text()
            .append(Component.text("✓ ", NamedTextColor.GREEN))
            .append(Component.text(courseName, NamedTextColor.YELLOW))
            .append(Component.text(" - Checkpoint #", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(checkpointIndex), NamedTextColor.AQUA))
            .append(Component.text(" Point 1 saved: ", NamedTextColor.GRAY))
            .append(Component.text(coords, NamedTextColor.WHITE))
            .append(Component.text(" (Click Point 2)", NamedTextColor.GRAY))
            .build();
        player.sendActionBar(message);
        
        // Particles
        Location particleLoc = blockLoc.clone().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            particleLoc,
            6, 0.2, 0.2, 0.2, 0.01
        );
    }
    
    /**
     * Send feedback for checkpoint point 2 capture (checkpoint complete)
     */
    public static void sendCheckpointCompleteFeedback(Player player, String courseName, int checkpointIndex, Location blockLoc) {
        // Sound (slightly higher pitch for completion)
        player.playSound(blockLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        
        // ActionBar message
        String coords = String.format("%d, %d, %d", blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        Component message = Component.text()
            .append(Component.text("✓✓ ", NamedTextColor.GREEN))
            .append(Component.text(courseName, NamedTextColor.YELLOW))
            .append(Component.text(" - Checkpoint #", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(checkpointIndex), NamedTextColor.AQUA))
            .append(Component.text(" Point 2 saved: ", NamedTextColor.GRAY))
            .append(Component.text(coords, NamedTextColor.WHITE))
            .append(Component.text(" (Checkpoint complete!)", NamedTextColor.GREEN))
            .build();
        player.sendActionBar(message);
        
        // Particles (more for completion)
        Location particleLoc = blockLoc.clone().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(
            Particle.FIREWORK,
            particleLoc,
            12, 0.3, 0.3, 0.3, 0.02
        );
    }
}
