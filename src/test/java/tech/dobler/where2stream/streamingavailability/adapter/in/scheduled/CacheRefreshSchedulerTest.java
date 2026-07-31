package tech.dobler.where2stream.streamingavailability.adapter.in.scheduled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.streamingavailability.application.BackgroundCacheRefreshService;

import static org.mockito.Mockito.verify;

/**
 * The cron wiring itself is Spring-framework territory (no own-code risk); this only checks the
 * scheduled method actually delegates to the service.
 */
@ExtendWith(MockitoExtension.class)
class CacheRefreshSchedulerTest {

    @Mock
    private BackgroundCacheRefreshService backgroundCacheRefreshService;
    @InjectMocks
    private CacheRefreshScheduler scheduler;

    @Test
    void refreshDueEntriesDelegatesToTheService() {
        scheduler.refreshDueEntries();

        verify(backgroundCacheRefreshService).refreshDueEntries();
    }
}
