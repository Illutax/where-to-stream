package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.dobler.where2stream.purchaseoffers.domain.UpstreamQuotaExhaustedException;

import java.time.Duration;

/**
 * Configures the {@code ebay} circuit breaker used by {@link EbayBrowseApiSource}.
 *
 * <p><strong>In Java rather than in {@code application.properties}</strong>, for two reasons that
 * both bit during development:
 * <ul>
 *   <li>{@code ignoreExceptions} takes a fully qualified class name as a <em>string</em> in
 *       properties. A typo there binds to nothing and fails silently, leaving the breaker to open
 *       on an exhausted daily quota — the one condition it must ignore. Here it is a compile-time
 *       class reference that a rename cannot break.</li>
 *   <li>{@code src/test/resources/application.properties} shadows the production file on the test
 *       classpath, so property-based settings are invisible to every {@code @SpringBootTest} and
 *       would have to be duplicated to be testable at all.</li>
 * </ul>
 * A {@code CircuitBreakerConfigCustomizer} still leaves the values overridable per environment
 * through the usual resilience4j properties, should that ever be wanted.
 */
@Configuration
public class EbayCircuitBreakerConfig {

    /** Must match the instance name on {@code EbayBrowseApiSource.findOffers}. */
    public static final String INSTANCE = "ebay";

    @Bean
    public CircuitBreakerConfigCustomizer ebayCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of(INSTANCE, builder -> builder
                // A failure RATE over a window, not a run of consecutive failures: a source that
                // rejects every second call would never trip a consecutive-failure counter while
                // burning half the daily budget on failures.
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(5))
                // Half-open lets a couple of trial calls through rather than reopening the
                // floodgates the moment the wait elapses.
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // A spent daily allowance is the upstream working correctly, not an unhealthy one.
                // Counting it would open the breaker on the very day the quota ledger has already
                // closed (ADR-0017).
                .ignoreExceptions(UpstreamQuotaExhaustedException.class));
    }
}
