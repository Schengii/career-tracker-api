package de.schenk.careertracker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * JWT settings. {@code secret} should be provided via the JWT_SECRET environment variable
 * (at least 32 bytes). If it is blank, a random key is generated at startup, which is
 * fine for local development but invalidates all tokens on every restart.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT1H") Duration expiration,
        @DefaultValue("P7D") Duration refreshExpiration) {
}
