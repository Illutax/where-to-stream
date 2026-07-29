package tech.dobler.where2stream.accountaccess.domain;

/**
 * A user's UI colour-scheme preference. {@link #SYSTEM} follows the OS setting
 * ({@code prefers-color-scheme}); {@link #LIGHT} / {@link #DARK} force one.
 */
public enum Theme {
    SYSTEM,
    LIGHT,
    DARK
}
