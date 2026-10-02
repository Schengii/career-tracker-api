package de.schenk.careertracker.service;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Job application with id " + id + " not found");
    }
}
