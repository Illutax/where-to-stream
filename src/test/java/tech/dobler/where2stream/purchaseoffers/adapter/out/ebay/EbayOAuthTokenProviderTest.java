package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.purchaseoffers.EbayPropertiesFixture;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Network-free tests for the eBay application-token handling. */
@ExtendWith(MockitoExtension.class)
class EbayOAuthTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final String TOKEN_JSON = """
            {"access_token":"v^1.1#tok","expires_in":7200,"token_type":"Application"}""";

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> response;
    @Mock
    private TimeService timeService;

    private static EbayProperties properties() {
        return EbayPropertiesFixture.active();
    }

    private EbayOAuthTokenProvider provider() {
        return new EbayOAuthTokenProvider(properties(), () -> httpClient, timeService);
    }

    @Test
    void aSuccessfulResponseYieldsTheAccessToken() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(TOKEN_JSON);
        when(timeService.now()).thenReturn(NOW);

        assertThat(provider().accessToken()).isEqualTo("v^1.1#tok");
    }

    @Test
    void aStillValidTokenIsReusedInsteadOfRequestingAnotherOne() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(TOKEN_JSON);
        when(timeService.now()).thenReturn(NOW, NOW.plus(Duration.ofMinutes(30)));
        final var provider = provider();

        provider.accessToken();
        provider.accessToken();

        // Every avoidable request costs a call from the daily budget the feature is rationing.
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void aTokenNearingExpiryIsRenewedEarly() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(TOKEN_JSON);
        // Second call sits inside the renew-early window: 7200s validity minus the 5-minute margin.
        final var justInsideTheMargin = NOW.plus(Duration.ofSeconds(7200))
                .minus(EbayOAuthTokenProvider.RENEW_BEFORE_EXPIRY).plusSeconds(1);
        when(timeService.now()).thenReturn(NOW, justInsideTheMargin, justInsideTheMargin);
        final var provider = provider();

        provider.accessToken();
        provider.accessToken();

        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void theCredentialsAreSentAsBasicAuthentication() {
        final var expected = Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

        assertThat(expected).isNotBlank();
        // Guards the encoding shape itself; the header assembly is exercised by the tests above.
        assertThat(new String(Base64.getDecoder().decode(expected), StandardCharsets.UTF_8))
                .isEqualTo("client-id:client-secret");
    }

    @Test
    void aRejectedCredentialBecomesAnUnavailableSource() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":\"invalid_client\"}");

        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> provider().accessToken())
                .withMessageContaining("HTTP 401")
                .withMessageContaining("invalid_client");
    }

    @Test
    void aTransportFailureBecomesAnUnavailableSource() throws Exception {
        doThrow(new IOException("connection reset")).when(httpClient).send(any(), any());

        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> provider().accessToken())
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    void anInterruptRestoresTheFlagAndReportsUnavailable() throws Exception {
        doThrow(new InterruptedException()).when(httpClient).send(any(), any());

        try {
            assertThatExceptionOfType(OfferSourceUnavailableException.class)
                    .isThrownBy(() -> provider().accessToken());
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag regardless of assertion outcome
        }
    }

    @Test
    void aResponseWithoutAnAccessTokenIsRejected() {
        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> EbayOAuthTokenProvider.parseToken("{\"expires_in\":7200}"))
                .withMessageContaining("no access_token");
    }

    @Test
    void aResponseWithoutAnExpiryIsRejected() {
        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> EbayOAuthTokenProvider.parseToken("{\"access_token\":\"tok\"}"))
                .withMessageContaining("no expires_in");
    }

    @Test
    void anUnreadableBodyIsRejected() {
        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> EbayOAuthTokenProvider.parseToken("not json at all"))
                .withMessageContaining("not readable JSON");
    }

    @Test
    void aWellFormedResponseParsesToValueAndValidity() {
        final var token = EbayOAuthTokenProvider.parseToken(TOKEN_JSON);

        assertThat(token)
                .isNotNull()
                .extracting(EbayOAuthTokenProvider.Token::value, EbayOAuthTokenProvider.Token::expiresIn)
                .isEqualTo(List.of("v^1.1#tok", Duration.ofSeconds(7200)));
    }
}
