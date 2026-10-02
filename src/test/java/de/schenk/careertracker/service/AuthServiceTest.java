package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.AppUser;
import de.schenk.careertracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registerNormalizesUsernameAndStoresOnlyAHash() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);

        String username = service().register("  Alice ", "correct-horse");

        assertThat(username).isEqualTo("alice");
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsExistingUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service().register("ALICE", "correct-horse"))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticateAcceptsCorrectPassword() {
        AppUser user = new AppUser("alice", passwordEncoder.encode("correct-horse"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service().authenticate("Alice", "correct-horse")).isEqualTo("alice");
    }

    @Test
    void authenticateRejectsWrongPasswordAndUnknownUserAlike() {
        AppUser user = new AppUser("alice", passwordEncoder.encode("correct-horse"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().authenticate("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service().authenticate("nobody", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
