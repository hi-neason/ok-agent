package io.okagent.module.observe.application;

import io.okagent.module.observe.infrastructure.persistence.TraceSpanRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Periodically removes expired execution traces so diagnostic payloads do not live indefinitely. */
@Component
public class TraceRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(TraceRetentionJob.class);
    private static final int MAX_RETENTION_DAYS = 3_650;

    private final TraceSpanRepository repository;
    private final int retentionDays;

    public TraceRetentionJob(
            TraceSpanRepository repository,
            @Value("${ok-agent.observe.trace-retention-days:30}") int retentionDays) {
        if (retentionDays < 1 || retentionDays > MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException("Trace retention days must be between 1 and 3650");
        }
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${ok-agent.observe.trace-cleanup-cron:0 17 3 * * *}", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        int deleted = purgeExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired trace span(s)", deleted);
        }
    }

    int purgeExpired(Instant now) {
        return repository.deleteCreatedBefore(now.minus(retentionDays, ChronoUnit.DAYS));
    }
}
