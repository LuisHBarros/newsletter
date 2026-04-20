package com.assine.subscriptions.application.outbox;

import com.assine.subscriptions.domain.outbox.model.OutboxEvent;
import com.assine.subscriptions.domain.outbox.model.OutboxEventStatus;
import com.assine.subscriptions.domain.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisherService publisherService;
    private final MeterRegistry meterRegistry;

    private static final int BATCH_SIZE = 50;

    @PostConstruct
    public void registerMetrics() {
        meterRegistry.gauge("outbox.pending.count",
            Tags.of("service", "subscriptions"),
            outboxEventRepository,
            repo -> repo.countByStatus(OutboxEventStatus.PENDING));
    }

    /**
     * Process pending events that are due for publishing.
     * No @Transactional here - each event is processed in its own transaction (REQUIRES_NEW).
     * This prevents one failing event from rolling back all others in the batch.
     */
    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    public void processPendingEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> dueEvents = outboxEventRepository.findDueForPublishing(BATCH_SIZE, now);

        if (dueEvents.isEmpty()) {
            return;
        }

        log.info("Processing {} due outbox events (limit: {})", dueEvents.size(), BATCH_SIZE);

        for (OutboxEvent event : dueEvents) {
            publisherService.publishWithRetry(event);
        }
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanupOldPublishedEvents() {
        Instant cutoffDate = Instant.now().minus(java.time.Duration.ofDays(7));
        int deletedCount = outboxEventRepository.deleteOldPublishedEvents(cutoffDate);
        log.info("Deleted {} old published events (older than 7 days)", deletedCount);
    }
}
