package tech.dobler.where2stream.shared.platform.concurrency;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ADR-0016 dedup guarantee, pinned directly: everywhere else the tracker only appears as a
 * mock, so until this test nothing would have noticed its two methods changing behaviour.
 */
class RefreshInFlightTrackerTest {

    @Test
    void aTitleStartsOnlyOnceUntilFinishedWhileOtherTitlesStayIndependent() {
        final var tracker = new RefreshInFlightTracker();
        final var tt1 = ImdbId.of("tt1");

        assertThat(tracker.tryStart(tt1)).as("first start").isTrue();
        assertThat(tracker.tryStart(tt1)).as("duplicate while in flight").isFalse();
        assertThat(tracker.tryStart(ImdbId.of("tt2"))).as("unrelated title").isTrue();

        tracker.finish(tt1);
        assertThat(tracker.tryStart(tt1)).as("restart after finish").isTrue();
    }
}
