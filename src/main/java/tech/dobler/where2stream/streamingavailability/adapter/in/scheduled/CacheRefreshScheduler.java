package tech.dobler.where2stream.streamingavailability.adapter.in.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.dobler.where2stream.streamingavailability.application.BackgroundCacheRefreshService;

/**
 * Scheduled entry point for the proactive background cache refresh (ADR-0016).
 * A coarse, once-daily default cadence — a safety net for titles nobody is actively viewing, not
 * a substitute for the demand-driven refresh in {@code StreamInfoService.resolveAll}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wer-streamt.background-refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheRefreshScheduler {

    private final BackgroundCacheRefreshService backgroundCacheRefreshService;

    @Scheduled(cron = "${wer-streamt.background-refresh.cron:0 0 4 * * *}")
    public void refreshDueEntries() {
        log.info("Scheduled cache refresh starting");
        final int queued = backgroundCacheRefreshService.refreshDueEntries();
        log.info("Scheduled cache refresh finished: {} titles queued for background refresh", queued);
    }
}
