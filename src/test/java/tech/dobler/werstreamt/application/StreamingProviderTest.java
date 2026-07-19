package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingProviderTest {

    @Test
    void keyIsTheLowerCaseName() {
        assertThat(StreamingProvider.AMAZON.key()).isEqualTo("amazon");
        assertThat(StreamingProvider.GOOGLE.key()).isEqualTo("google");
    }

    @Test
    void serviceNamesMatchWerstreamtEs() {
        assertThat(StreamingProvider.AMAZON.serviceName()).isEqualTo("Prime Video");
        assertThat(StreamingProvider.DISNEY.serviceName()).isEqualTo("Disney+");
        assertThat(StreamingProvider.WOW.serviceName()).isEqualTo("WOW");
    }

    @Test
    void offeringFlagsDescribeWhichListsAPageHas() {
        // Amazon shows both, flatrate-only providers only included, Google only paid.
        assertThat(StreamingProvider.AMAZON.hasFlatrate()).isTrue();
        assertThat(StreamingProvider.AMAZON.hasPaid()).isTrue();
        assertThat(StreamingProvider.NETFLIX.hasFlatrate()).isTrue();
        assertThat(StreamingProvider.NETFLIX.hasPaid()).isFalse();
        assertThat(StreamingProvider.GOOGLE.hasFlatrate()).isFalse();
        assertThat(StreamingProvider.GOOGLE.hasPaid()).isTrue();
    }

    @Test
    void fromKeyResolvesKnownKeysCaseInsensitively() {
        assertThat(StreamingProvider.fromKey("netflix")).contains(StreamingProvider.NETFLIX);
        assertThat(StreamingProvider.fromKey("NETFLIX")).contains(StreamingProvider.NETFLIX);
    }

    @Test
    void fromKeyIsEmptyForUnknownOrNullKeys() {
        assertThat(StreamingProvider.fromKey("hbo")).isEmpty();
        assertThat(StreamingProvider.fromKey(null)).isEmpty();
    }
}
