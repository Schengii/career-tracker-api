package de.schenk.careertracker.web.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
