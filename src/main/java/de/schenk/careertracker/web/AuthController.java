package de.schenk.careertracker.web;

import de.schenk.careertracker.security.TokenService;
import de.schenk.careertracker.service.AuthService;
import de.schenk.careertracker.web.dto.LoginRequest;
import de.schenk.careertracker.web.dto.RegisterRequest;
import de.schenk.careertracker.web.dto.TokenResponse;
import de.schenk.careertracker.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return new UserResponse(authService.register(request.username(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String username = authService.authenticate(request.username(), request.password());
        return new TokenResponse(tokenService.issue(username), "Bearer", tokenService.expiresInSeconds());
    }
}
