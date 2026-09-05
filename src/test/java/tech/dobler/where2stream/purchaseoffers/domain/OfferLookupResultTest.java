package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OfferLookupResultTest {

    private static final TitleOffers OFFERS =
            TitleOffers.none(ImdbId.of("tt0113277"), Instant.parse("2026-09-05T14:00:00Z"));

    @Test
    void aFetchedResultCarriesItsOffers() {
        assertThat(OfferLookupResult.fetched(OFFERS))
                .isNotNull()
                .extracting(OfferLookupResult::status, OfferLookupResult::offers)
                .isEqualTo(List.of(OfferLookupResult.Status.FETCHED, Optional.of(OFFERS)));
    }

    @Test
    void aRefusalCarriesNoOffers() {
        assertThat(OfferLookupResult.unavailable())
                .isNotNull()
                .extracting(OfferLookupResult::status, OfferLookupResult::offers)
                .isEqualTo(List.of(OfferLookupResult.Status.UNAVAILABLE, Optional.empty()));
    }

    @Test
    void theTwoQuotaRefusalsStayDistinguishable() {
        // The client renders them differently: waiting helps with one, not with the other.
        assertThat(OfferLookupResult.of(QuotaVerdict.USER_ALLOWANCE_REACHED).status())
                .isEqualTo(OfferLookupResult.Status.USER_ALLOWANCE_REACHED);
        assertThat(OfferLookupResult.of(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED).status())
                .isEqualTo(OfferLookupResult.Status.GLOBAL_BUDGET_EXHAUSTED);
    }

    @Test
    void anAllowedVerdictIsNotARefusalAndIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OfferLookupResult.of(QuotaVerdict.ALLOWED))
                .withMessageContaining("not a refusal");
    }

    @Test
    void aFetchedResultWithoutOffersIsRejected() {
        // Guards the invariant the DTO mapping relies on: FETCHED means there is something to map.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OfferLookupResult(OfferLookupResult.Status.FETCHED, Optional.empty()))
                .withMessageContaining("must carry its offers");
    }

    @Test
    void aRefusalThatCarriesOffersIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OfferLookupResult(OfferLookupResult.Status.UNAVAILABLE, Optional.of(OFFERS)))
                .withMessageContaining("Only a FETCHED result");
    }

    @Test
    void aNullOptionalIsRejectedSoAbsenceIsAlwaysEmpty() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OfferLookupResult(OfferLookupResult.Status.UNAVAILABLE, null));
    }
}
