package tech.dobler.werstreamt.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;
import tech.dobler.werstreamt.time.TimeService;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Bootstraps an initial ADMIN account when the user table is empty, so a fresh deployment is
 * usable. The password comes from {@code w2s.security.initial-admin.password}; if unset, a strong
 * one is generated and logged once (à la Spring Boot's default user) and must be changed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TimeService timeService;
    private final SecurityProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        final var admin = properties.initialAdmin();
        var rawPassword = admin.password();
        final boolean generated = rawPassword == null || rawPassword.isBlank();
        if (generated) {
            rawPassword = UUID.randomUUID().toString();
        }

        users.save(AppUser.local(admin.username(), passwordEncoder.encode(rawPassword), null,
                EnumSet.of(Role.ADMIN, Role.USER), timeService.now()));

        if (generated) {
            log.warn("""

                    ================================================================
                    Seeded initial admin '{}' with a GENERATED password:

                        {}

                    Set w2s.security.initial-admin.password to choose your own, and
                    change it after first login.
                    ================================================================""",
                    admin.username(), rawPassword);
        } else {
            log.info("Seeded initial admin '{}' from configuration.", admin.username());
        }
    }
}
