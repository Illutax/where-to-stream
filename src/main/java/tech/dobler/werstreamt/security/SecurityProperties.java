package tech.dobler.werstreamt.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code w2s.security.*} configuration: the initial admin bootstrapped on an empty user table,
 * and the "remember me" persistent-login settings.
 *
 * @param initialAdmin the admin seeded when no users exist yet
 * @param rememberMe   persistent-login (survives restarts and browser close)
 */
@ConfigurationProperties(prefix = "w2s.security")
public record SecurityProperties(
        @DefaultValue InitialAdmin initialAdmin,
        @DefaultValue RememberMe rememberMe
) {

    /**
     * @param username initial admin username (default {@code admin})
     * @param password initial admin password; if blank, a strong one is generated and logged once
     */
    public record InitialAdmin(@DefaultValue("admin") String username, String password) {
    }

    /**
     * @param key          stable secret for the remember-me token; MUST be set in production so
     *                     tokens survive restarts. If blank, a random one is generated and logged
     *                     (which means remember-me resets on every restart).
     * @param validityDays how long the remember-me login stays valid (default 14 days)
     */
    public record RememberMe(String key, @DefaultValue("14") int validityDays) {
    }
}
