package com.bocrace.util;

import com.bocrace.BOCRacingV2;
import org.bukkit.NamespacedKey;

/**
 * Persistent Data Container keys for tracking race boats
 */
public class PDCKeys {
    
    private final BOCRacingV2 plugin;
    
    // Keys for boat metadata
    public final NamespacedKey raceBoat;
    public final NamespacedKey playerUuid;
    public final NamespacedKey courseName;
    public final NamespacedKey runId;
    
    public PDCKeys(BOCRacingV2 plugin) {
        this.plugin = plugin;
        
        // Initialize all PDC keys
        this.raceBoat = new NamespacedKey(plugin, "race_boat");
        this.playerUuid = new NamespacedKey(plugin, "player_uuid");
        this.courseName = new NamespacedKey(plugin, "course_name");
        this.runId = new NamespacedKey(plugin, "run_id");
    }
}
