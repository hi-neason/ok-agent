package io.okagent.module.observe.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.okagent.module.observe.domain.SpanStatus;
import io.okagent.module.observe.domain.SpanType;
import io.okagent.module.observe.domain.TraceSpan;
import io.okagent.module.observe.infrastructure.persistence.TraceSpanRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceServiceImplTests {

    @Test
    void ignoresBestEffortWritesAfterShutdown() {
        var repository = mock(TraceSpanRepository.class);
        var service = new TraceServiceImpl(repository);
        service.shutdown();
        var span = new TraceSpan(
                "trace",
                "span",
                null,
                "session",
                null,
                "user",
                1,
                SpanType.AGENT,
                "agent",
                1,
                2,
                SpanStatus.OK,
                "{}",
                "",
                "");

        assertThatCode(() -> service.saveAll(List.of(span))).doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }
}
