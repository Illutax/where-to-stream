package tech.dobler.where2stream.shared.platform.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Background execution for cache refreshes (ADR-0016): demand-driven (stale entry hit on a page
 * request) and the scheduled proactive job ({@code CacheRefreshScheduler}) both submit to this
 * single executor. {@code @EnableScheduling} lives here too since both annotations enable the
 * same underlying concern (work running off the request thread).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Deliberately small: the shared {@code RateLimiter} already throttles outbound
     * werstreamt.es requests to a configured requests/second (default 2), so more threads here
     * would only add context-switching, not throughput.
     */
    @Bean("cacheRefreshExecutor")
    public Executor cacheRefreshExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cache-refresh-");
        executor.initialize();
        return executor;
    }
}
