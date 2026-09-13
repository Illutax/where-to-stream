package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The remember-me fallback rule: a configured key is used verbatim (logins survive restarts),
 * no key or a blank one falls back to a generated transient secret instead of an empty string.
 */
class RememberMeKeyTest {

    private static SecurityProperties withKey(String key) {
        return new SecurityProperties(new SecurityProperties.InitialAdmin("admin", null),
                new SecurityProperties.RememberMe(key, 14));
    }

    @Test
    void aConfiguredKeyIsUsedVerbatim() {
        assertThat(SecurityConfig.rememberMeKey(withKey("stable-secret"))).isEqualTo("stable-secret");
    }

    @Test
    void aMissingOrBlankKeyFallsBackToAGeneratedTransientSecret() {
        assertThat(SecurityConfig.rememberMeKey(withKey(null))).isNotBlank();
        assertThat(SecurityConfig.rememberMeKey(withKey("  "))).isNotBlank().isNotEqualTo("  ");
    }
}
