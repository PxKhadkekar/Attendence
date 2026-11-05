package com.college.placement.attendance.entity;

public enum DriveStatus {
    DRAFT,
    SCHEDULED,
    OPEN,
    CLOSED,
    FINALIZED;

    public boolean canTransitionTo(DriveStatus next) {
        return switch (this) {
            case DRAFT -> next == SCHEDULED;
            case SCHEDULED -> next == OPEN;
            case OPEN -> next == CLOSED;
            case CLOSED -> next == OPEN || next == FINALIZED;
            case FINALIZED -> false; // Finalized records cannot transition
        };
    }
}
