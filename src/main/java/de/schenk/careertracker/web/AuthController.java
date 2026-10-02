package de.schenk.careertracker.web;

import de.schenk.careertracker.security.LoginRateLimiter;
import de.schenk.careertracker.security.TokenService;
import de.schenk.careertracker.service.AuthService;
import de.schenk.careertracker.service.InvalidCredentialsException;
import de.schenk.careertracker.service.RefreshTokenService;
import de.schenk.careertracker.web.dto.LoginRequest;
import de.schenk.careertracker.web.dto.RefreshRequest;
import de.schenk.careertracker.web.dto.RegisterRequest;
import de.schenk.careertracker.web.dto.TokenResponse;
import de.schenk.careertracker.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, TokenService tokenService,
                          RefreshTokenService refreshTokenService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return new UserResponse(authService.register(request.username(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String key = http.getRemoteAddr() + "|" + request.username().trim().toLowerCase(Locale.ROOT);
        loginRateLimiter.checkAllowed(key);
        String username;
        try {
            username = authService.authenticate(request.username(), request.password());
        } catch (InvalidCredentialsException ex) {
            loginRateLimiter.recordFailure(key);
            throw ex;
        }
        loginRateLimiter.reset(key);
        return issuePair(username);
    }

    /** Exchanges a refresh token for a new access token and a new refresh token (rotation). */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return issuePair(refreshTokenService.rotate(request.refreshToken()));
    }

    /** Revokes the given refresh token. Always answers 204 so the endpoint reveals nothing. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse issuePair(String username) {
        return new TokenResponse(tokenService.issue(username), refreshTokenService.issue(username),
                "Bearer", tokenService.expiresInSeconds());
    }
}
