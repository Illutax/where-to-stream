package tech.dobler.werstreamt.application;

import java.util.Locale;
import java.util.Optional;

/**
 * The streaming providers w2s knows about. Maps a stable, URL-friendly key
 * (e.g. {@code "amazon"}) to the werstreamt.es service name (e.g. {@code "Prime Video"}) and
 * records which kinds of offering the provider page shows.
 *
 * <p>{@code hasFlatrate}/{@code hasPaid} let a single {@code ProviderPageService} render all
 * five pages uniformly: Amazon shows both, Disney+/Netflix/WOW only the flatrate, Google Play
 * only paid titles.
 */
public enum StreamingProvider {
    AMAZON("Prime Video", true, true),
    DISNEY("Disney+", true, false),
    NETFLIX("Netflix", true, false),
    WOW("WOW", true, false),
    GOOGLE("Google Play", false, true);

    private final String serviceName;
    private final boolean hasFlatrate;
    private final boolean hasPaid;

    StreamingProvider(String serviceName, boolean hasFlatrate, boolean hasPaid) {
        this.serviceName = serviceName;
        this.hasFlatrate = hasFlatrate;
        this.hasPaid = hasPaid;
    }

    public String serviceName() {
        return serviceName;
    }

    public boolean hasFlatrate() {
        return hasFlatrate;
    }

    public boolean hasPaid() {
        return hasPaid;
    }

    /** The lower-case key used in URLs and the Angular router, e.g. {@code "amazon"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resolves a provider from its URL key ({@code amazon|disney|netflix|wow|google}); case-insensitive. */
    public static Optional<StreamingProvider> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (StreamingProvider provider : values()) {
            if (provider.key().equals(key.toLowerCase(Locale.ROOT))) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
