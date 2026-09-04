package io.okagent.module.observe.application;

import io.okagent.module.observe.domain.TraceSpan;
import io.okagent.module.observe.infrastructure.persistence.TraceSpanRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * In-process persistence for execution traces. Implements both {@link TraceService} (read API) and
 * {@link TraceSink} (write callback used by {@link TraceCollectingMiddleware}).
 *
 * <p>Writes run on a small daemon thread pool and are best-effort: trace persistence must never
 * block or fail an agent reply. This deliberately avoids HTTP/OTLP — spans flow straight from the
 * framework middleware to MySQL through this service.
 */
@Service
public class TraceServiceImpl implements TraceService, TraceSink {

    private static final Logger log = LoggerFactory.getLogger(TraceServiceImpl.class);

    private final TraceSpanRepository repository;
    private final ExecutorService writer;

    public TraceServiceImpl(TraceSpanRepository repository) {
        this.repository = repository;
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "okagent-trace-writer-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.writer = Executors.newFixedThreadPool(2, factory);
    }

    @Override
    public void saveAll(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(
                () -> {
                    try {
                        repository.saveAll(spans);
                    } catch (Exception e) {
                        log.warn(
                                "Failed to persist {} span(s) for trace {}: {}",
                                spans.size(),
                                spans.get(0).getTraceId(),
                                e.getMessage(),
                                e);
                    }
                },
                writer);
    }

    @Override
    public Optional<List<TraceSpan>> findTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return Optional.empty();
        }
        List<TraceSpan> spans = repository.findByTraceIdOrderByIdAsc(traceId);
        return spans.isEmpty() ? Optional.empty() : Optional.of(spans);
    }
}
