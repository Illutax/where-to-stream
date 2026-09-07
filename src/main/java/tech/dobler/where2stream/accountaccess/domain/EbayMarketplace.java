package tech.dobler.where2stream.accountaccess.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The eBay marketplaces a user may pick, which decides where the search link next to a title opens
 * (TODO-57).
 *
 * <p>Lives here, in the context that owns the preference, and holds nothing but the three ids.
 * It replaces a richer enum that lived in {@code purchaseoffers} and carried currency, marketplace
 * header id and a host allowlist — all of it in service of the withdrawn price lookup (TODO-56).
 * None of that has a reader any more: the link is assembled in the browser, so the server's only
 * remaining interest in a marketplace is whether the string a user sent names one.
 *
 * <p>That also retires the inversion this validation used to need. While the ids lived in another
 * context, {@code accountaccess} could not name them and had to ask through a {@code port.spi}
 * contract ({@code SupportedMarketplaces}, ADR-0019). With the owning context gone, the question
 * and the answer are in the same place and the indirection would be ceremony —
 * ADR-0019 keeps its remaining case, {@code PosterAttributionProvider}.
 */
public enum EbayMarketplace {

    EBAY_DE,
    EBAY_US,
    EBAY_GB;

    private static final Set<String> IDS = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    /** Every acceptable id, for validation and for telling the user what is allowed. */
    public static Set<String> ids() {
        return IDS;
    }

    /**
     * Whether a stored or submitted id names a marketplace we know.
     *
     * <p>Answers on the string rather than on the enum on purpose: {@code app_user} keeps the
     * preference in a {@code varchar}, and a value written when this enum looked different must be
     * something we can ask about without it throwing.
     */
    public static boolean supports(String marketplaceId) {
        return marketplaceId != null && IDS.contains(marketplaceId);
    }
}
