package tech.dobler.where2stream.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;
import tech.dobler.where2stream.domain.AuthProvider;
import tech.dobler.where2stream.domain.Role;
import tech.dobler.where2stream.persistence.AppUser;
import tech.dobler.where2stream.persistence.AppUserRepository;
import tech.dobler.where2stream.time.TimeService;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real {@code OidcUserService} delegate is replaced with a mock via {@link ReflectionTestUtils}
 * since it's a {@code final} field initialized inline (not constructor-injected, hence not
 * autowired by Lombok's {@code @RequiredArgsConstructor}) — this keeps the test network-free.
 */
@ExtendWith(MockitoExtension.class)
class GoogleOidcUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AppUserRepository users;
    @Mock
    private TimeService timeService;
    @Mock
    private OidcUserService delegate;

    private GoogleOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOidcUserService(users, timeService);
        ReflectionTestUtils.setField(service, "delegate", delegate);
    }

    /** {@code sub} is required by {@link OidcIdToken}; {@code email} is present only when given. */
    private static OidcUser upstreamOidcUser(String email) {
        final var claims = email == null
                ? Map.<String, Object>of("sub", "google-123")
                : Map.<String, Object>of("sub", "google-123", "email", email);
        final var idToken = new OidcIdToken("token-value", Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(600), claims);
        return new DefaultOidcUser(Set.of(), idToken, "sub");
    }

    @Test
    void provisionsANewLocalUserOnFirstLogin() {
        when(delegate.loadUser(any())).thenReturn(upstreamOidcUser("alice@example.com"));
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.empty());
        when(timeService.now()).thenReturn(NOW);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final OidcUser result = service.loadUser(null);

        final var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        final var saved = captor.getValue();
        assertThat(saved).extracting(AppUser::getUsername, AppUser::getEmail, AppUser::getProvider)
                .containsExactly("alice@example.com", "alice@example.com", AuthProvider.GOOGLE);
        assertThat(saved.getRoles()).containsExactly(Role.USER);

        assertThat(result.getName()).isEqualTo("alice@example.com");
        assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void reusesTheExistingLocalUserOnRepeatLoginWithoutSavingAgain() {
        final var existing = AppUser.fromProvider("alice@example.com", "alice@example.com", AuthProvider.GOOGLE,
                EnumSet.of(Role.USER, Role.ADMIN), NOW);
        when(delegate.loadUser(any())).thenReturn(upstreamOidcUser("alice@example.com"));
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(existing));

        final OidcUser result = service.loadUser(null);

        verify(users, never()).save(any());
        assertThat(result.getName()).isEqualTo("alice@example.com");
        assertThat(result.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void throwsWhenTheOidcResponseHasNoEmail() {
        when(delegate.loadUser(any())).thenReturn(upstreamOidcUser(null));

        assertThatThrownBy(() -> service.loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo("invalid_user_info");
        verify(users, never()).save(any());
    }

    @Test
    void throwsWhenTheLocalAccountIsDisabled() {
        final var disabled = AppUser.fromProvider("alice@example.com", "alice@example.com", AuthProvider.GOOGLE,
                EnumSet.of(Role.USER), NOW);
        disabled.setEnabled(false);
        when(delegate.loadUser(any())).thenReturn(upstreamOidcUser("alice@example.com"));
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo("account_disabled");
    }
}
