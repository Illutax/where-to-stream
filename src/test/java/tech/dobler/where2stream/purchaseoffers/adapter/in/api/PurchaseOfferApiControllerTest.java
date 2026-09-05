package tech.dobler.where2stream.purchaseoffers.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.purchaseoffers.application.TitleOfferService;
import tech.dobler.where2stream.purchaseoffers.domain.Offer;
import tech.dobler.where2stream.purchaseoffers.domain.OfferLookupResult;
import tech.dobler.where2stream.purchaseoffers.domain.OfferPrice;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaVerdict;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurchaseOfferApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class PurchaseOfferApiControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ImdbId HEAT = ImdbId.of("tt0113277");
    private static final Instant FETCHED_AT = Instant.parse("2026-09-05T14:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TitleOfferService titleOfferService;
    @MockitoBean
    private CurrentUserPort currentUserPort;

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "pw");
    }

    private static OfferLookupResult withBothOffers() {
        final var buyNow = new Offer(OfferPrice.of(1299, "EUR"), Optional.of(OfferPrice.of(399, "EUR")),
                URI.create("https://www.ebay.de/itm/1"));
        final var auction = Offer.withoutStatedShipping(OfferPrice.of(450, "EUR"),
                URI.create("https://www.ebay.de/itm/2"));
        return OfferLookupResult.fetched(
                new TitleOffers(HEAT, Optional.of(buyNow), Optional.of(auction), FETCHED_AT));
    }

    @Test
    void bothPricesAreReturnedWithTheirShippingAndTimestamp() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT)).thenReturn(withBothOffers());

        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FETCHED"))
                .andExpect(jsonPath("$.buyNow.amountCents").value(1299))
                .andExpect(jsonPath("$.buyNow.shippingCents").value(399))
                .andExpect(jsonPath("$.buyNow.currency").value("EUR"))
                .andExpect(jsonPath("$.buyNow.url").value("https://www.ebay.de/itm/1"))
                .andExpect(jsonPath("$.auction.amountCents").value(450))
                .andExpect(jsonPath("$.fetchedAt").exists());
    }

    @Test
    void unstatedShippingIsNullRatherThanZero() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT)).thenReturn(withBothOffers());

        // The client must be able to tell "ships free" from "shipping cost unknown".
        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(jsonPath("$.auction.shippingCents").doesNotExist());
    }

    @Test
    void noOffersFoundIsASuccessfulResponseNotAnError() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT))
                .thenReturn(OfferLookupResult.fetched(TitleOffers.none(HEAT, FETCHED_AT)));

        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FETCHED"))
                .andExpect(jsonPath("$.buyNow").doesNotExist())
                .andExpect(jsonPath("$.auction").doesNotExist());
    }

    @Test
    void aTitleNotOnTheCallersWatchlistIsA404() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(eq(USER), any()))
                .thenThrow(new ValidationException(HttpStatus.NOT_FOUND, "No such title on your watchlist."));

        // Without this the endpoint would be an authenticated proxy to eBay search for any title.
        mockMvc.perform(get("/api/titles/tt0133093/offers").principal(alice()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theEndpointTakesAnIdAndOffersNoWayToPassASearchTerm() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT)).thenReturn(withBothOffers());

        // A stray query parameter must not reach eBay: the search term is built server-side from
        // the watchlist entry, and the signature has nowhere to put anything else (plan 5.1).
        mockMvc.perform(get("/api/titles/tt0113277/offers").param("q", "anything").principal(alice()))
                .andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLookupIsPrivatelyCacheable() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT)).thenReturn(withBothOffers());

        // private, never public: the answer depends on the caller's watchlist and is charged to
        // their personal allowance.
        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(header().string("Cache-Control", "max-age=300, private"));
    }

    @Test
    void anExhaustedBudgetIsNotCached() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT))
                .thenReturn(OfferLookupResult.of(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED));

        // Caching a refusal would keep reporting it after the situation has changed.
        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GLOBAL_BUDGET_EXHAUSTED"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void aPersonalAllowanceIsReportedDistinctlyFromTheSharedOne() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT))
                .thenReturn(OfferLookupResult.of(QuotaVerdict.USER_ALLOWANCE_REACHED));

        // The difference matters to the reader: their own limit vs. everyone's.
        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USER_ALLOWANCE_REACHED"));
    }

    @Test
    void anUnavailableSourceIsReportedAsSuchAndNotCached() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT))
                .thenReturn(OfferLookupResult.unavailable());

        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.fetchedAt").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void noSellerOrListingDetailLeaksIntoTheResponse() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(titleOfferService.lookupForWatchlistTitle(USER, HEAT)).thenReturn(withBothOffers());

        // Section 5.3: the less third-party user-generated content reaches the client, the smaller
        // the XSS surface — and the less there is to break when eBay changes a field.
        mockMvc.perform(get("/api/titles/tt0113277/offers").principal(alice()))
                .andExpect(jsonPath("$.buyNow.title").doesNotExist())
                .andExpect(jsonPath("$.buyNow.seller").doesNotExist())
                .andExpect(jsonPath("$.buyNow.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.buyNow.condition").doesNotExist());
    }
}
