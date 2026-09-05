package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

/**
 * Update the current user's eBay marketplace for price lookups.
 *
 * <p>Validates only that something was sent (ADR-0015). Whether the value is an <em>acceptable</em>
 * marketplace is checked in the service, because that answer belongs to another context and a
 * record cannot ask it.
 */
public record EbayMarketplaceUpdateCommand(String username, String marketplace) {
    public EbayMarketplaceUpdateCommand {
        if (marketplace == null || marketplace.isBlank()) {
            throw new ValidationException("A marketplace is required.");
        }
    }
}
