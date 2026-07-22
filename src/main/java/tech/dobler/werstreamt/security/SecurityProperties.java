package tech.dobler.werstreamt.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code w2s.security.*} configuration — currently the initial admin bootstrapped on an empty
 * user table.
 *
 * @param initialAdmin the admin seeded when no users exist yet
 */
@ConfigurationProperties(prefix = "w2s.security")
public record SecurityProperties(@DefaultValue InitialAdmin initialAdmin) {

    /**
     * @param username initial admin username (default {@code admin})
     * @param password initial admin password; if blank, a strong one is generated and logged once
     */
    public record InitialAdmin(@DefaultValue("admin") String username, String password) {
    }
}
