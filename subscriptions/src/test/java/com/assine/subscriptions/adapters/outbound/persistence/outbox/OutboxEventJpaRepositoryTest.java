package com.assine.subscriptions.adapters.outbound.persistence.outbox;

import com.assine.subscriptions.domain.outbox.model.OutboxEvent;
import com.assine.subscriptions.domain.outbox.model.OutboxEventStatus;
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

    @Test
    void shouldSaveAndFindOutboxEvent() {
        OutboxEvent event = OutboxEvent.builder()
                .id(testId)
                .eventId(testId)
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-123"))
                .status(OutboxEventStatus.PENDING)
                .build();

        repository.save(event);

        assertThat(repository.findById(testId)).isPresent();
        assertThat(repository.findById(testId).get().getEventType()).isEqualTo("SubscriptionCreated");
    }

    @Test
    void shouldFindByStatus() {
        OutboxEvent pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-123"))
                .status(OutboxEventStatus.PENDING)
                .build();

        OutboxEvent publishedEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-456"))
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(Instant.now())
                .build();

        repository.save(pendingEvent);
        repository.save(publishedEvent);

        List<OutboxEvent> pendingEvents = repository.findByStatus(OutboxEventStatus.PENDING);
        List<OutboxEvent> publishedEvents = repository.findByStatus(OutboxEventStatus.PUBLISHED);

        assertThat(pendingEvents).hasSize(1);
        assertThat(publishedEvents).hasSize(1);
    }

    @Test
    void shouldFindByStatusOrderByCreatedAt() {
        Instant now = Instant.now();
        
        OutboxEvent event1 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-1"))
                .status(OutboxEventStatus.PENDING)
                .createdAt(now.minusSeconds(10))
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-2"))
                .status(OutboxEventStatus.PENDING)
                .createdAt(now.minusSeconds(5))
                .build();

        repository.save(event1);
        repository.save(event2);

        List<OutboxEvent> events = repository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getCreatedAt()).isBefore(events.get(1).getCreatedAt());
    }

    @Test
    void shouldFindByAggregateTypeAndAggregateId() {
        UUID aggregateId1 = UUID.randomUUID();
        UUID aggregateId2 = UUID.randomUUID();

        OutboxEvent event1 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId1)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-1"))
                .status(OutboxEventStatus.PENDING)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId2)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-2"))
                .status(OutboxEventStatus.PENDING)
                .build();

        repository.save(event1);
        repository.save(event2);

        List<OutboxEvent> events = repository.findByAggregateTypeAndAggregateId("Subscription", aggregateId1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAggregateId()).isEqualTo(aggregateId1);
    }

    @Test
    void shouldUpdateStatus() {
        OutboxEvent event = OutboxEvent.builder()
                .id(testId)
                .eventId(testId)
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-123"))
                .status(OutboxEventStatus.PENDING)
                .build();

        repository.save(event);
        repository.updateStatus(testId, OutboxEventStatus.PUBLISHED);

        repository.flush();
        OutboxEvent updated = repository.findById(testId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(updated.getProcessedAt()).isNotNull();
    }

    @Test
    void shouldMarkAsFailed() {
        OutboxEvent event = OutboxEvent.builder()
                .id(testId)
                .eventId(testId)
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-123"))
                .status(OutboxEventStatus.PENDING)
                .build();

        repository.save(event);
        repository.markAsFailed(testId, OutboxEventStatus.FAILED, "Connection error");

        repository.flush();
        OutboxEvent updated = repository.findById(testId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(updated.getLastError()).isEqualTo("Connection error");
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    void shouldIncrementRetryCount() {
        OutboxEvent event = OutboxEvent.builder()
                .id(testId)
                .eventId(testId)
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-123"))
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

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
        
        OutboxEvent oldEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-1"))
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(java.time.Duration.ofDays(10)))
                .build();

        OutboxEvent recentEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-2"))
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(java.time.Duration.ofDays(3)))
                .build();

        OutboxEvent pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-3"))
                .status(OutboxEventStatus.PENDING)
                .build();

        repository.save(oldEvent);
        repository.save(recentEvent);
        repository.save(pendingEvent);

        int deleted = repository.deleteByStatusAndProcessedAtBefore(OutboxEventStatus.PUBLISHED, now.minus(java.time.Duration.ofDays(7)));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(oldEvent.getId())).isEmpty();
        assertThat(repository.findById(recentEvent.getId())).isPresent();
        assertThat(repository.findById(pendingEvent.getId())).isPresent();
    }
}
