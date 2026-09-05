package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;

import static org.assertj.core.api.Assertions.assertThat;

class EbayPropertiesTest {

    private static final String SECRET = "PRD-abcdef123456-super-secret";

    private static EbayProperties properties(boolean enabled, String clientId, String clientSecret) {
        return new EbayProperties(enabled, clientId, clientSecret, "https://api.ebay.com",
                Marketplace.EBAY_DE, "617", 3, new EbayProperties.RateLimit(2));
    }

    @Test
    void theSecretDoesNotAppearInToString() {
        final var props = properties(true, "client-id-value", SECRET);

        // The point of the override: a record's generated toString() would print both credentials,
        // and a single binding failure at startup logs the bound record.
        assertThat(props.toString())
                .doesNotContain(SECRET)
                .doesNotContain("client-id-value")
                .contains("clientSecret=<set>", "clientId=<set>");
    }

    @Test
    void toStringDistinguishesAMissingCredentialFromAPresentOne() {
        final var props = properties(true, "client-id-value", "");

        assertThat(props.toString()).contains("clientId=<set>", "clientSecret=<unset>");
    }

    @Test
    void toStringStillCarriesTheNonSecretConfiguration() {
        final var props = properties(true, "id", SECRET);

        assertThat(props.toString())
                .contains("enabled=true", "apiBaseUrl=https://api.ebay.com",
                        "defaultMarketplace=EBAY_DE", "categoryId=617", "resultsPerQuery=3");
    }

    @Test
    void theIntegrationIsInactiveWithoutTheFlag() {
        assertThat(properties(false, "id", SECRET).active()).isFalse();
    }

    @Test
    void theIntegrationIsInactiveWhenEitherCredentialIsMissing() {
        assertThat(properties(true, "", SECRET).active()).isFalse();
        assertThat(properties(true, "id", " ").active()).isFalse();
    }

    @Test
    void theIntegrationIsActiveOnlyWithTheFlagAndBothCredentials() {
        assertThat(properties(true, "id", SECRET).active()).isTrue();
    }
}
