package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.JobStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(JobStatus from, JobStatus to) {
        super("Status transition " + from + " -> " + to + " is not allowed");
    }
}
