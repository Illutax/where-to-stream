package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.UpstreamQuotaExhaustedException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the {@link EbayCircuitBreakerConfig#INSTANCE} circuit-breaker instance is actually configured as intended.
 *
 * <p>The configuration itself lives in {@link EbayCircuitBreakerConfig} rather than in properties,
 * for reasons documented there. This test is what verifies the customizer is actually picked up:
 * a bean that is never applied leaves the breaker on resilience4j's defaults (100 calls, 60 s) and
 * says nothing about it. The quota case matters most — a breaker that opened on an exhausted daily
 * allowance would suppress lookups on exactly the day the quota ledger has already handled.
 */
@SpringBootTest
class EbayCircuitBreakerConfigurationTest {

    @Autowired
    private CircuitBreakerRegistry registry;

    /**
     * The Spring context is cached across tests, so the breaker instance is shared and its state
     * would leak from one test into the next.
     */
    @BeforeEach
    void resetTheBreaker() {
        registry.circuitBreaker(EbayCircuitBreakerConfig.INSTANCE).reset();
    }

    @Test
    void theEbayInstanceUsesAFailureRateOverASlidingWindow() {
        final var config = registry.circuitBreaker(EbayCircuitBreakerConfig.INSTANCE).getCircuitBreakerConfig();

        assertThat(config)
                .isNotNull()
                .extracting(c -> c.getSlidingWindowSize(), c -> c.getMinimumNumberOfCalls(),
                        c -> c.getFailureRateThreshold(), c -> c.getPermittedNumberOfCallsInHalfOpenState())
                .isEqualTo(List.of(10, 5, 50.0f, 2));
    }

    @Test
    void theBreakerStaysOpenLongEnoughToBeWorthOpening() {
        final var config = registry.circuitBreaker(EbayCircuitBreakerConfig.INSTANCE).getCircuitBreakerConfig();

        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofMinutes(5).toMillis());
    }

    @Test
    void anExhaustedDailyQuotaIsNotTreatedAsAnUpstreamFailure() {
        final var breaker = registry.circuitBreaker(EbayCircuitBreakerConfig.INSTANCE);

        // Asserted behaviourally rather than against a predicate: resilience4j keeps the ignore
        // list in its own predicate, separate from the record one, and reading the wrong one is an
        // easy way to write a test that passes for the wrong reason.
        for (int i = 0; i < 20; i++) {
            breaker.onError(0, TimeUnit.MILLISECONDS, new UpstreamQuotaExhaustedException("errorId 2001"));
        }

        assertThat(breaker.getState().name()).isEqualTo("CLOSED");
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void anOrdinaryUpstreamFailureDoesCountTowardsOpening() {
        final var breaker = registry.circuitBreaker(EbayCircuitBreakerConfig.INSTANCE);

        for (int i = 0; i < 10; i++) {
            breaker.onError(0, TimeUnit.MILLISECONDS,
                    new OfferSourceUnavailableException("connection reset"));
        }

        assertThat(breaker.getState().name()).isEqualTo("OPEN");
    }
}
