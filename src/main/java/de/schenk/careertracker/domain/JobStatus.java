package de.schenk.careertracker.domain;

/**
 * Lifecycle of a job application. Terminal states cannot be left again.
 */
public enum JobStatus {
    APPLIED,
    INTERVIEW,
    OFFER,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    /**
     * Returns whether an application in this status may move to {@code next}.
     * Staying in the same status is always allowed (e.g. when only notes change).
     */
    public boolean canTransitionTo(JobStatus next) {
        if (next == this) {
            return true;
        }
        return switch (this) {
            case APPLIED -> next == INTERVIEW || next == REJECTED || next == WITHDRAWN;
            case INTERVIEW -> next == OFFER || next == REJECTED || next == WITHDRAWN;
            case OFFER -> next == ACCEPTED || next == REJECTED || next == WITHDRAWN;
            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN;
    }
}
