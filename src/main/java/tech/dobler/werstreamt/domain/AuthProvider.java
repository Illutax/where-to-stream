package tech.dobler.werstreamt.domain;

/** How a user authenticates: a local password, or an external OIDC provider. */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
