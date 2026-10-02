package de.schenk.careertracker.web.dto;

import de.schenk.careertracker.domain.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for creating or updating a job application.
 * {@code status} defaults to APPLIED and {@code appliedAt} to today when omitted on creation.
 */
public record JobApplicationRequest(
        @NotBlank @Size(max = 120) String company,
        @NotBlank @Size(max = 120) String position,
        JobStatus status,
        @PastOrPresent LocalDate appliedAt,
        @Size(max = 2000) String notes
) {
}
