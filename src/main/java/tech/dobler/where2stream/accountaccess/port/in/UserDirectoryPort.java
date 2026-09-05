package tech.dobler.where2stream.accountaccess.port.in;

import java.util.Optional;
import java.util.UUID;

/**
 * Published facts about the user base that other bounded contexts need.
 *
 * <p>Published as a port rather than letting other contexts reach for {@code AppUserRepository},
 * which the isolation rules in {@code ArchitectureTest} forbid — and rightly so: a caller that
 * needs a head count or one preference should not gain access to user rows.
 */
public interface UserDirectoryPort {

    /**
     * How many users are registered right now.
     *
     * <p>Used by Purchase Offers as the {@code n} in the daily quota split {@code 10000 / n}
     * (ADR-0017).
     */
    long registeredUserCount();

    /**
     * The eBay marketplace this user picked, as its id, or empty for an unknown user or one who
     * never chose.
     *
     * <p>A {@link String}, not a {@code Marketplace}: the enum belongs to Purchase Offers, and
     * Account &amp; Access neither imports it nor should appear to understand it. It stores and
     * returns the user's choice; interpreting it is the caller's business. The counterpart is
     * {@code SupportedMarketplaces}, through which the owning context says which values are
     * acceptable in the first place.
     */
    Optional<String> ebayMarketplaceOf(UUID userId);
}
