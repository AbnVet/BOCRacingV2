package com.bocrace.util;

import com.bocrace.model.Course;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a Course for completeness and correctness
 */
public class CourseValidator {
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean ok;
        private final List<String> issues;
        
        public ValidationResult(boolean ok, List<String> issues) {
            this.ok = ok;
            this.issues = issues;
        }
        
        public boolean isOk() {
            return ok;
        }
        
        public List<String> getIssues() {
            return issues;
        }
    }
    
    /**
     * Validate a course
     */
    public static ValidationResult validate(Course course) {
        List<String> issues = new ArrayList<>();
        
        // 1. Course lobby must be set and world loaded
        if (course.getCourseLobbySpawn() == null) {
            issues.add("Course lobby spawn is not set");
        } else {
            World lobbyWorld = course.getCourseLobbySpawn().getWorld();
            if (lobbyWorld == null) {
                issues.add("Course lobby world is not loaded");
            } else {
                String lobbyWorldName = lobbyWorld.getName();
                
                // 2. Player spawns: must be at least 1
                int spawnCount = course.getPlayerSpawns().size();
                if (spawnCount == 0) {
                    issues.add("Player spawns: 0 (at least 1 spawn required)");
                } else {
                    // Check all spawn worlds are loaded
                    for (int i = 0; i < spawnCount; i++) {
                        if (course.getPlayerSpawns().get(i).getWorld() == null) {
                            issues.add("Player spawn #" + (i + 1) + " world is not loaded");
                        }
                    }
                }
                
                // 3. Start region must exist and world loaded
                if (course.getStartRegion() == null) {
                    issues.add("Start region is not set");
                } else {
                    if (course.getStartRegion().getMin() == null || course.getStartRegion().getMax() == null) {
                        issues.add("Start region has invalid min/max bounds");
                    } else {
                        String startWorldName = course.getStartRegion().getWorld();
                        if (startWorldName == null) {
                            issues.add("Start region world name is missing");
                        } else {
                            World startWorld = Bukkit.getWorld(startWorldName);
                            if (startWorld == null) {
                                issues.add("Start region world '" + startWorldName + "' is not loaded");
                            } else if (!startWorldName.equals(lobbyWorldName)) {
                                issues.add("Start region world '" + startWorldName + "' does not match course lobby world '" + lobbyWorldName + "'");
                            }
                        }
                    }
                }
                
                // 4. Finish region must exist and world loaded
                if (course.getFinishRegion() == null) {
                    issues.add("Finish region is not set");
                } else {
                    if (course.getFinishRegion().getMin() == null || course.getFinishRegion().getMax() == null) {
                        issues.add("Finish region has invalid min/max bounds");
                    } else {
                        String finishWorldName = course.getFinishRegion().getWorld();
                        if (finishWorldName == null) {
                            issues.add("Finish region world name is missing");
                        } else {
                            World finishWorld = Bukkit.getWorld(finishWorldName);
                            if (finishWorld == null) {
                                issues.add("Finish region world '" + finishWorldName + "' is not loaded");
                            } else if (!finishWorldName.equals(lobbyWorldName)) {
                                issues.add("Finish region world '" + finishWorldName + "' does not match course lobby world '" + lobbyWorldName + "'");
                            }
                        }
                    }
                }
                
                // 5. Checkpoints validation (if required)
                Course.CourseSettings settings = course.getSettings();
                if (settings != null && settings.getRules().isRequireCheckpoints()) {
                    int checkpointCount = course.getCheckpoints().size();
                    if (checkpointCount == 0) {
                        issues.add("Checkpoints required but none set (settings.rules.requireCheckpoints=true)");
                    } else {
                        // Validate sequential indexing
                        List<Integer> indices = new ArrayList<>();
                        for (Course.CheckpointRegion cp : course.getCheckpoints()) {
                            indices.add(cp.getCheckpointIndex());
                        }
                        indices.sort(Integer::compareTo);
                        for (int i = 0; i < indices.size(); i++) {
                            if (indices.get(i) != i + 1) {
                                issues.add("Checkpoints must have sequential indexing starting at 1 (found: " + indices + ")");
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        return new ValidationResult(issues.isEmpty(), issues);
    }
}
