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
                
                // 2. Player spawns: must be exactly 1 for SOLO (only SOLO supported now)
                int spawnCount = course.getPlayerSpawns().size();
                if (spawnCount == 0) {
                    issues.add("Player spawns: 0 (SOLO requires exactly 1 spawn)");
                } else if (spawnCount > 1) {
                    issues.add("Player spawns: " + spawnCount + " (Multiplayer not implemented yet; SOLO requires exactly 1 spawn)");
                } else {
                    // Exactly 1 spawn - check if world is loaded
                    if (course.getPlayerSpawns().get(0).getWorld() == null) {
                        issues.add("Player spawn world is not loaded");
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
                
                // 5. Checkpoints are optional - no validation needed
            }
        }
        
        return new ValidationResult(issues.isEmpty(), issues);
    }
}
