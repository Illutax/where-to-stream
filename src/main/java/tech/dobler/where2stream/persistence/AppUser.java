package tech.dobler.where2stream.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.dobler.where2stream.domain.AuthProvider;
import tech.dobler.where2stream.domain.Language;
import tech.dobler.where2stream.domain.Role;
import tech.dobler.where2stream.domain.Theme;
import tech.dobler.where2stream.domain.ViewMode;

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

    /** The user's UI colour-scheme preference (defaults to following the OS). */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false)
    private Theme theme = Theme.SYSTEM;

    /** Whether the user sees the FSK age-rating badges (on by default). */
    @Column(name = "show_age_ratings", nullable = false)
    private boolean showAgeRatings = true;

    /** The user's UI language preference (defaults to English). */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language = Language.EN;

    /** Whether film titles are shown in German where available (off by default). */
    @Column(name = "show_german_title", nullable = false)
    private boolean showGermanTitle = false;

    /** The user's preferred library layout (defaults to the poster-tile grid). */
    @Enumerated(EnumType.STRING)
    @Column(name = "view_mode", nullable = false)
    private ViewMode viewMode = ViewMode.GRID;

    /** Number of poster tiles per row in the grid view (2-6, defaults to 6). */
    @Column(name = "tiles_per_row", nullable = false)
    private int tilesPerRow = 6;

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

    public void changeTheme(Theme newTheme) {
        this.theme = newTheme;
    }

    public void changeShowAgeRatings(boolean show) {
        this.showAgeRatings = show;
    }

    public void changeLanguage(Language newLanguage) {
        this.language = newLanguage;
    }

    public void changeShowGermanTitle(boolean show) {
        this.showGermanTitle = show;
    }

    public void changeViewMode(ViewMode newViewMode) {
        this.viewMode = newViewMode;
    }

    public void changeTilesPerRow(int newTilesPerRow) {
        this.tilesPerRow = newTilesPerRow;
    }

    /** Change the login username (unique). Callers must ensure the new name is free. */
    public void rename(String newUsername) {
        this.username = newUsername;
    }
}
