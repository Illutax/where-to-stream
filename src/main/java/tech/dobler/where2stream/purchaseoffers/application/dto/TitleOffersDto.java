package tech.dobler.where2stream.purchaseoffers.application.dto;

import tech.dobler.where2stream.purchaseoffers.domain.OfferLookupResult;

import java.time.Instant;

/**
 * The response for one price lookup.
 *
 * <p>{@code status} is part of the payload rather than being encoded in the HTTP status, because
 * none of the outcomes is an error: "nobody is selling this", "we could not ask right now" and
 * "your daily allowance is spent" are all ordinary answers a price widget has to render. Returning
 * 4xx/5xx for them would turn a corner of the dashboard into an error state and lose the
 * distinction the user actually needs.
 *
 * @param status      one of {@code FETCHED}, {@code UNAVAILABLE}, {@code USER_ALLOWANCE_REACHED},
 *                    {@code GLOBAL_BUDGET_EXHAUSTED}
 * @param buyNow      cheapest fixed-price offer, or {@code null}
 * @param auction     cheapest running auction, or {@code null}
 * @param fetchedAt   when the prices were read, or {@code null} if nothing was read. Shown as an
 *                    "as of …" stamp so a price never looks more current than it is — a bid can
 *                    change within the minute
 */
public record TitleOffersDto(String status, OfferDto buyNow, OfferDto auction, Instant fetchedAt) {

    public static TitleOffersDto from(OfferLookupResult result) {
        return result.offers()
                .map(offers -> new TitleOffersDto(
                        result.status().name(),
                        offers.buyNow().map(OfferDto::from).orElse(null),
                        offers.auction().map(OfferDto::from).orElse(null),
                        offers.fetchedAt()))
                .orElseGet(() -> new TitleOffersDto(result.status().name(), null, null, null));
    }
}
