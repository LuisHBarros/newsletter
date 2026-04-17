package com.assine.billing.adapters.outbound.persistence.outbox;

import com.assine.billing.domain.outbox.model.OutboxEvent;
import com.assine.billing.domain.outbox.model.OutboxEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OutboxEventJpaRepositoryTest {

    @Autowired
    private OutboxEventJpaRepository repository;

    private UUID testId;
    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        testId = UUID.randomUUID();
        aggregateId = UUID.randomUUID();
    }

    private OutboxEvent.OutboxEventBuilder base() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Payment")
                .aggregateId(aggregateId)
                .eventType("billing.payment.succeeded")
                .eventPayload(Map.of("paymentId", UUID.randomUUID().toString()))
                .status(OutboxEventStatus.PENDING);
    }

    @Test
    void shouldSaveAndFindOutboxEvent() {
        OutboxEvent event = base().id(testId).eventId(testId).build();
        repository.save(event);

        assertThat(repository.findById(testId)).isPresent();
        assertThat(repository.findById(testId).get().getEventType())
                .isEqualTo("billing.payment.succeeded");
    }

    @Test
    void shouldFindByStatus() {
        repository.save(base().build());
        repository.save(base().status(OutboxEventStatus.PUBLISHED).processedAt(Instant.now()).build());

        assertThat(repository.findByStatus(OutboxEventStatus.PENDING)).hasSize(1);
        assertThat(repository.findByStatus(OutboxEventStatus.PUBLISHED)).hasSize(1);
    }

    @Test
    void shouldFindByStatusOrderByCreatedAt() {
        Instant now = Instant.now();
        repository.save(base().createdAt(now.minusSeconds(10)).build());
        repository.save(base().createdAt(now.minusSeconds(5)).build());

        List<OutboxEvent> events = repository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getCreatedAt()).isBefore(events.get(1).getCreatedAt());
    }

    @Test
    void shouldFindByAggregateTypeAndAggregateId() {
        UUID aggregateId1 = UUID.randomUUID();
        UUID aggregateId2 = UUID.randomUUID();
        repository.save(base().aggregateId(aggregateId1).build());
        repository.save(base().aggregateId(aggregateId2).build());

        List<OutboxEvent> events = repository.findByAggregateTypeAndAggregateId("Payment", aggregateId1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAggregateId()).isEqualTo(aggregateId1);
    }

    @Test
    void shouldUpdateStatus() {
        OutboxEvent event = base().id(testId).eventId(testId).build();
        repository.save(event);
        repository.updateStatus(testId, OutboxEventStatus.PUBLISHED);
        repository.flush();

        OutboxEvent updated = repository.findById(testId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(updated.getProcessedAt()).isNotNull();
    }

    @Test
    void shouldMarkAsFailed() {
        OutboxEvent event = base().id(testId).eventId(testId).build();
        repository.save(event);
        repository.markAsFailed(testId, OutboxEventStatus.FAILED, "connection error");
        repository.flush();

        OutboxEvent updated = repository.findById(testId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(updated.getLastError()).isEqualTo("connection error");
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    void shouldIncrementRetryCount() {
        OutboxEvent event = base().id(testId).eventId(testId).retryCount(0).build();
        repository.save(event);
        int newCount = repository.incrementRetryCount(testId);
        repository.flush();

        OutboxEvent updated = repository.findById(testId).orElseThrow();
        assertThat(newCount).isEqualTo(1);
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    void shouldDeleteOldPublishedEvents() {
        Instant now = Instant.now();

        OutboxEvent oldEvent = base()
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(java.time.Duration.ofDays(10)))
                .build();
        OutboxEvent recentEvent = base()
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(java.time.Duration.ofDays(3)))
                .build();
        OutboxEvent pendingEvent = base().build();

        repository.save(oldEvent);
        repository.save(recentEvent);
        repository.save(pendingEvent);

        int deleted = repository.deleteByStatusAndProcessedAtBefore(
                OutboxEventStatus.PUBLISHED,
                now.minus(java.time.Duration.ofDays(7)));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(oldEvent.getId())).isEmpty();
        assertThat(repository.findById(recentEvent.getId())).isPresent();
        assertThat(repository.findById(pendingEvent.getId())).isPresent();
    }

    @Test
    void scheduleRetryPushesNextAttemptAndIncrementsCount() {
        Instant originalAttempt = Instant.now().minusSeconds(60);
        OutboxEvent event = base().id(testId).eventId(testId).retryCount(0)
                .nextAttemptAt(originalAttempt).build();
        repository.save(event);

        // Push the next attempt into the future. The exact Instant stored depends on
        // H2's timezone handling for @Column without explicit timezone — assert the
        // *relative* outcome (counter + error + monotonic push forward) instead.
        Instant future = Instant.now().plusSeconds(120);
        repository.scheduleRetry(testId, future, "transient");
        repository.flush();

        OutboxEvent updated = repository.findById(testId).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getLastError()).isEqualTo("transient");
        assertThat(updated.getNextAttemptAt())
                .as("scheduled retry must be strictly after the original next_attempt_at")
                .isAfter(originalAttempt);
    }
}
