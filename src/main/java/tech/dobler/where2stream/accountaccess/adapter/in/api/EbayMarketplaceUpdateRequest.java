package tech.dobler.where2stream.accountaccess.adapter.in.api;

/** Body of {@code PUT /api/me/ebay-marketplace} (missing or unknown value → 400). */
public record EbayMarketplaceUpdateRequest(String marketplace) {
}
