package com.assine.subscriptions.application.outbox;

import com.assine.subscriptions.adapters.outbound.persistence.outbox.OutboxEventJpaRepository;
import com.assine.subscriptions.domain.outbox.model.OutboxEvent;
import com.assine.subscriptions.domain.outbox.model.OutboxEventStatus;
import com.assine.subscriptions.domain.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Testcontainers
class OutboxEventE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.instance-profile", () -> false);
        registry.add("spring.cloud.aws.sqs.endpoint", localstack::getEndpoint);
        registry.add("spring.cloud.aws.secrets-manager.endpoint", localstack::getEndpoint);
        registry.add("aws.sqs.events.queue", () -> "assine-events");
        registry.add("aws.sqs.billing.queue", () -> "assine-billing");
    }

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @MockBean
    private com.assine.subscriptions.domain.outbox.port.EventPublisher eventPublisher;

    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        outboxEventJpaRepository.deleteAll();
        aggregateId = UUID.randomUUID();
    }

    @Test
    void shouldCreateAndProcessOutboxEvent() {
        doNothing().when(eventPublisher).publish(anyString(), anyString(), anyString(), any());

        OutboxEvent event = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId,
                Map.of("userId", "user-123", "planId", "plan-456")
        );

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getEventType()).isEqualTo("SubscriptionCreated");

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent).isNotNull();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void shouldMarkEventAsPublished() {
        OutboxEvent event = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId,
                Map.of("userId", "user-123")
        );

        outboxEventService.markAsPublished(event.getId());

        OutboxEvent updatedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(updatedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    void shouldMarkEventAsFailed() {
        OutboxEvent event = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId,
                Map.of("userId", "user-123")
        );

        outboxEventService.markAsFailed(event.getId(), "Connection error");

        OutboxEvent updatedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(updatedEvent.getLastError()).isEqualTo("Connection error");
        assertThat(updatedEvent.getRetryCount()).isEqualTo(1);
    }

    @Test
    void shouldIncrementRetryCount() {
        OutboxEvent event = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId,
                Map.of("userId", "user-123")
        );

        outboxEventService.incrementRetryCount(event.getId());

        OutboxEvent updatedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updatedEvent.getRetryCount()).isEqualTo(1);
    }

    @Test
    void shouldDeleteOldPublishedEvents() {
        Instant now = Instant.now();

        // Create old published event (10 days old)
        OutboxEvent oldEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-old"))
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(java.time.Duration.ofDays(10)))
                .build();

        // Create recent published event (3 days old)
        OutboxEvent recentEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-recent"))
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(java.time.Duration.ofDays(3)))
                .build();

        // Create pending event (should not be deleted)
        OutboxEvent pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(aggregateId)
                .eventType("SubscriptionCreated")
                .eventPayload(Map.of("userId", "user-pending"))
                .status(OutboxEventStatus.PENDING)
                .build();

        outboxEventJpaRepository.save(oldEvent);
        outboxEventJpaRepository.save(recentEvent);
        outboxEventJpaRepository.save(pendingEvent);

        Instant cutoffDate = now.minus(java.time.Duration.ofDays(7));
        int deletedCount = outboxEventRepository.deleteOldPublishedEvents(cutoffDate);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(outboxEventRepository.findById(oldEvent.getId())).isEmpty();
        assertThat(outboxEventRepository.findById(recentEvent.getId())).isPresent();
        assertThat(outboxEventRepository.findById(pendingEvent.getId())).isPresent();
    }

    @Test
    void shouldGetPendingEvents() {
        OutboxEvent pendingEvent1 = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId,
                Map.of("userId", "user-1")
        );

        OutboxEvent pendingEvent2 = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                UUID.randomUUID(),
                Map.of("userId", "user-2")
        );

        OutboxEvent publishedEvent = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                UUID.randomUUID(),
                Map.of("userId", "user-3")
        );
        outboxEventService.markAsPublished(publishedEvent.getId());

        var pendingEvents = outboxEventService.getPendingEvents();

        assertThat(pendingEvents).hasSize(2);
        assertThat(pendingEvents).allMatch(e -> e.getStatus() == OutboxEventStatus.PENDING);
    }

    @Test
    void shouldGetEventsByStatus() {
        OutboxEvent pendingEvent = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId,
                Map.of("userId", "user-1")
        );

        OutboxEvent publishedEvent = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                UUID.randomUUID(),
                Map.of("userId", "user-2")
        );
        outboxEventService.markAsPublished(publishedEvent.getId());

        var publishedEvents = outboxEventService.getEventsByStatus(OutboxEventStatus.PUBLISHED);
        var pendingEvents = outboxEventService.getEventsByStatus(OutboxEventStatus.PENDING);

        assertThat(publishedEvents).hasSize(1);
        assertThat(pendingEvents).hasSize(1);
    }

    @Test
    void shouldGetEventsByAggregate() {
        UUID aggregateId1 = UUID.randomUUID();
        UUID aggregateId2 = UUID.randomUUID();

        OutboxEvent event1 = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId1,
                Map.of("userId", "user-1")
        );

        OutboxEvent event2 = outboxEventService.createEvent(
                "SubscriptionUpdated",
                "Subscription",
                aggregateId1,
                Map.of("userId", "user-1")
        );

        OutboxEvent event3 = outboxEventService.createEvent(
                "SubscriptionCreated",
                "Subscription",
                aggregateId2,
                Map.of("userId", "user-2")
        );

        var events = outboxEventService.getEventsByAggregate("Subscription", aggregateId1);

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.getAggregateId().equals(aggregateId1));
    }
}
