package de.schenk.careertracker.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
