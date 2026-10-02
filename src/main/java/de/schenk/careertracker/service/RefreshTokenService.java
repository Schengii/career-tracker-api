package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.RefreshToken;
import de.schenk.careertracker.repository.RefreshTokenRepository;
import de.schenk.careertracker.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque, rotating refresh tokens. Every refresh invalidates the presented token and issues a
 * new one. Presenting an already used token is treated as theft: all tokens of that user are revoked.
 */
@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Creates and stores a new refresh token and returns its raw value (shown to the client once). */
    public String issue(String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(hash(raw), username, Instant.now().plus(properties.refreshExpiration())));
        return raw;
    }

    /**
     * Validates and consumes a refresh token and returns the owning username.
     * The caller issues the replacement pair. Reuse of a consumed token revokes all tokens of the
     * user, so the revocation must survive the exception (hence {@code noRollbackFor}).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public String rotate(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked()) {
            repository.revokeAllForUsername(token.getUsername());
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        token.revoke();
        return token.getUsername();
    }

    /** Logout: revokes the token if it exists; unknown tokens are ignored (idempotent). */
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
