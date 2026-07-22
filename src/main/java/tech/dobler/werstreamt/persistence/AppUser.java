package tech.dobler.werstreamt.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.dobler.werstreamt.domain.AuthProvider;
import tech.dobler.werstreamt.domain.Role;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A user account. Either local (with a BCrypt {@code passwordHash}) or provisioned from an
 * external OIDC provider ({@code passwordHash == null}, {@code provider != LOCAL}).
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /** BCrypt hash for local accounts; {@code null} for OIDC-only accounts. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email")
    private String email;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private AppUser(String username, String passwordHash, String email, Set<Role> roles,
                    AuthProvider provider, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = EnumSet.copyOf(roles.isEmpty() ? EnumSet.of(Role.USER) : roles);
        this.provider = provider;
        this.createdAt = createdAt;
        this.enabled = true;
    }

    /** A local account with a pre-encoded password hash. */
    public static AppUser local(String username, String passwordHash, String email, Set<Role> roles, Instant createdAt) {
        return new AppUser(username, passwordHash, email, roles, AuthProvider.LOCAL, createdAt);
    }

    /** An account provisioned from an external OIDC provider (no local password). */
    public static AppUser fromProvider(String username, String email, AuthProvider provider, Set<Role> roles, Instant createdAt) {
        return new AppUser(username, null, email, roles, provider, createdAt);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void setRoles(Set<Role> newRoles) {
        this.roles = EnumSet.copyOf(newRoles.isEmpty() ? EnumSet.of(Role.USER) : newRoles);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
