package com.bocrace.setup;

import com.bocrace.model.Course.BlockCoord;

/**
 * Tracks a single admin's setup session
 */
public class SetupSession {
    
    private String courseName;
    private ArmedAction armedAction;
    private BlockCoord pendingPoint1; // For two-click regions (start/finish/checkpoint)
    
    public SetupSession(String courseName, ArmedAction armedAction) {
        this.courseName = courseName;
        this.armedAction = armedAction;
    }
    
    public String getCourseName() {
        return courseName;
    }
    
    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }
    
    public ArmedAction getArmedAction() {
        return armedAction;
    }
    
    public void setArmedAction(ArmedAction armedAction) {
        this.armedAction = armedAction;
        // Clear pending point when changing action
        if (armedAction != ArmedAction.START && 
            armedAction != ArmedAction.FINISH && 
            armedAction != ArmedAction.CHECKPOINT) {
            this.pendingPoint1 = null;
        }
    }
    
    public BlockCoord getPendingPoint1() {
        return pendingPoint1;
    }
    
    public void setPendingPoint1(BlockCoord pendingPoint1) {
        this.pendingPoint1 = pendingPoint1;
    }
    
    public void clearPendingPoint1() {
        this.pendingPoint1 = null;
    }
    
    /**
     * Armed action types
     */
    public enum ArmedAction {
        PLAYER_SPAWN,
        COURSE_LOBBY,
        START,
        FINISH,
        CHECKPOINT,
        SOLO_JOIN_BUTTON,
        SOLO_RETURN_BUTTON,
        MP_LOBBY,
        MP_JOIN_BUTTON,
        MP_LEADER_CREATE_BUTTON,
        MP_LEADER_START_BUTTON,
        MP_LEADER_CANCEL_BUTTON
    }
}
