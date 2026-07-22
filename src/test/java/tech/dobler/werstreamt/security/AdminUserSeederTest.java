package tech.dobler.werstreamt.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserSeederTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TimeService timeService;

    private AdminUserSeeder seeder(String username, String password) {
        return new AdminUserSeeder(users, passwordEncoder, timeService,
                new SecurityProperties(new SecurityProperties.InitialAdmin(username, password)));
    }

    @Test
    void seedsAnAdminWhenTheUserTableIsEmpty() {
        when(users.count()).thenReturn(0L);
        when(timeService.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(passwordEncoder.encode("configured-pw")).thenReturn("{bcrypt}enc");

        seeder("root", "configured-pw").run(null);

        verify(users).save(org.mockito.ArgumentMatchers.<AppUser>argThat(u ->
                u.getUsername().equals("root")
                        && u.getPasswordHash().equals("{bcrypt}enc")
                        && u.getRoles().contains(Role.ADMIN)));
    }

    @Test
    void doesNothingWhenUsersAlreadyExist() {
        when(users.count()).thenReturn(3L);

        seeder("root", "pw").run(null);

        verify(users, never()).save(any());
    }
}
