package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Polls {@code outbox_events} for PENDING events due for publishing and delegates
 * publishing to {@link OutboxEventPublisherService} with per-event REQUIRES_NEW transactions.
 * Uses ShedLock for multi-instance safety.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "content.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisherService publisherService;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetrics() {
        meterRegistry.gauge("outbox.pending.count",
            Tags.of("service", "content"),
            outboxEventRepository,
            repo -> repo.countByStatus(OutboxEventStatus.PENDING));
    }

    @Scheduled(cron = "${content.outbox.relay.cron:*/5 * * * * *}")
    @SchedulerLock(name = "content-outbox-relay", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void drain() {
        List<OutboxEvent> due = outboxEventRepository.findDueForPublishing(BATCH_SIZE, Instant.now());
        if (due.isEmpty()) return;

        log.debug("Relaying {} outbox events", due.size());
        for (OutboxEvent e : due) {
            publisherService.publishWithRetry(e);
        }
    }
}
