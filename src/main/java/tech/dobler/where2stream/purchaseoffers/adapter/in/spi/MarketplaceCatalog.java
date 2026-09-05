package tech.dobler.where2stream.purchaseoffers.adapter.in.spi;

import org.springframework.stereotype.Component;
import tech.dobler.where2stream.accountaccess.port.spi.SupportedMarketplaces;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers Account &amp; Access's question about valid marketplace ids from the {@link Marketplace}
 * enum — the one place that knows them.
 *
 * <p>Derived rather than listed: a marketplace added to the enum becomes selectable without anyone
 * remembering to update a second list, and one removed stops being accepted immediately.
 */
@Component
public class MarketplaceCatalog implements SupportedMarketplaces {

    private static final Set<String> IDS = Arrays.stream(Marketplace.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public Set<String> ids() {
        return IDS;
    }
}
