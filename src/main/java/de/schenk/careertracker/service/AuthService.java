package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.AppUser;
import de.schenk.careertracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a user; usernames are case-insensitive and stored in lower case.
     */
    public String register(String username, String rawPassword) {
        String normalized = normalize(username);
        if (userRepository.existsByUsername(normalized)) {
            throw new UsernameAlreadyExistsException(normalized);
        }
        userRepository.save(new AppUser(normalized, passwordEncoder.encode(rawPassword)));
        return normalized;
    }

    /**
     * Verifies credentials and returns the normalized username.
     * Unknown user and wrong password are deliberately indistinguishable.
     */
    @Transactional(readOnly = true)
    public String authenticate(String username, String rawPassword) {
        String normalized = normalize(username);
        AppUser user = userRepository.findByUsername(normalized).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user.getUsername();
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
