package de.schenk.careertracker.web.dto;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;

import java.time.Instant;
import java.time.LocalDate;

public record JobApplicationResponse(
        Long id,
        String company,
        String position,
        JobStatus status,
        LocalDate appliedAt,
        String notes,
        Instant createdAt
) {
    public static JobApplicationResponse from(JobApplication entity) {
        return new JobApplicationResponse(
                entity.getId(),
                entity.getCompany(),
                entity.getPosition(),
                entity.getStatus(),
                entity.getAppliedAt(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }
}
