package tech.dobler.werstreamt.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.AuthProvider;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;
import tech.dobler.werstreamt.time.TimeService;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps a Google OIDC login onto a local {@link AppUser}: the first login provisions a local
 * account (role {@code USER}, provider {@code GOOGLE}) keyed by e-mail, and the authorities come
 * from that local account — so roles (incl. later ADMIN grants) are managed in one place.
 */
@Service
@RequiredArgsConstructor
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final AppUserRepository users;
    private final TimeService timeService;
    private final OidcUserService delegate = new OidcUserService();

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        final OidcUser oidcUser = delegate.loadUser(userRequest);
        final String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), "OIDC login without an e-mail");
        }

        final AppUser appUser = users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(AppUser.fromProvider(email, email, AuthProvider.GOOGLE,
                        EnumSet.of(Role.USER), timeService.now())));

        if (!appUser.isEnabled()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_disabled"), "Account is disabled");
        }

        final Set<GrantedAuthority> authorities = appUser.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
        // name attribute = "email" so authentication.getName() is the local username (the e-mail).
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
    }
}
