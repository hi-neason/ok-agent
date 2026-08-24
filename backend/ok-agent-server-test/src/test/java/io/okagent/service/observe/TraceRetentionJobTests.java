package io.okagent.service.observe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.okagent.repository.observe.TraceSpanRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TraceRetentionJobTests {

    @Test
    void deletesSpansOlderThanTheRetentionWindow() {
        TraceSpanRepository repository = mock(TraceSpanRepository.class);
        Instant now = Instant.parse("2026-08-24T06:00:00Z");
        Instant cutoff = now.minus(30, ChronoUnit.DAYS);
        when(repository.deleteCreatedBefore(cutoff)).thenReturn(7);

        new TraceRetentionJob(repository, 30).purgeExpired(now);

        verify(repository).deleteCreatedBefore(cutoff);
    }

    @Test
    void rejectsUnsafeRetentionConfiguration() {
        TraceSpanRepository repository = mock(TraceSpanRepository.class);

        assertThatThrownBy(() -> new TraceRetentionJob(repository, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 3650");
        assertThatThrownBy(() -> new TraceRetentionJob(repository, 3_651))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 3650");
    }
}
