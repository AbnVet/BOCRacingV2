package com.bocrace.storage;

import com.bocrace.BOCRacingV2;
import com.bocrace.model.DraftCourse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages draft course persistence as JSON files
 */
public class DraftManager {
    
    private final BOCRacingV2 plugin;
    private final Gson gson;
    private final File draftsFolder;
    
    public DraftManager(BOCRacingV2 plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Location.class, new LocationAdapter())
            .create();
        
        // Create drafts folder if it doesn't exist
        this.draftsFolder = new File(plugin.getDataFolder(), "drafts");
        if (!draftsFolder.exists()) {
            draftsFolder.mkdirs();
        }
    }
    
    /**
     * Save a draft course to JSON file
     */
    public void saveDraft(DraftCourse draft) throws IOException {
        File file = new File(draftsFolder, draft.getName() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(draft, writer);
        }
    }
    
    /**
     * Load a draft course from JSON file
     */
    public DraftCourse loadDraft(String courseName) throws IOException {
        File file = new File(draftsFolder, courseName + ".json");
        if (!file.exists()) {
            return null;
        }
        
        try (FileReader reader = new FileReader(file)) {
            DraftCourse draft = gson.fromJson(reader, DraftCourse.class);
            // Deserialize Locations from JSON (world name stored, need to resolve)
            return draft;
        }
    }
    
    /**
     * List all draft course names
     */
    public List<String> listDrafts() {
        List<String> drafts = new ArrayList<>();
        File[] files = draftsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                drafts.add(name.substring(0, name.length() - 5)); // Remove .json
            }
        }
        return drafts;
    }
    
    /**
     * Check if a draft exists
     */
    public boolean draftExists(String courseName) {
        File file = new File(draftsFolder, courseName + ".json");
        return file.exists();
    }
    
    /**
     * Delete a draft
     */
    public boolean deleteDraft(String courseName) {
        File file = new File(draftsFolder, courseName + ".json");
        return file.delete();
    }
}
