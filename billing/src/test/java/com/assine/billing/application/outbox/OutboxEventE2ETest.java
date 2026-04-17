package com.assine.billing.application.outbox;

import com.assine.billing.adapters.outbound.persistence.outbox.OutboxEventJpaRepository;
import com.assine.billing.domain.outbox.model.OutboxEvent;
import com.assine.billing.domain.outbox.model.OutboxEventStatus;
import com.assine.billing.domain.outbox.repository.OutboxEventRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * End-to-end outbox test: real Postgres + LocalStack SQS via Testcontainers,
 * {@link com.assine.billing.domain.outbox.port.EventPublisher} mocked so we
 * don't depend on actual SQS delivery.
 */
@SpringBootTest
@Testcontainers
class OutboxEventE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
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
        registry.add("aws.sqs.inbound.queue", () -> "assine-billing");
    }

    @Autowired private OutboxEventService outboxEventService;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

    @MockBean private com.assine.billing.domain.outbox.port.EventPublisher eventPublisher;

    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        outboxEventJpaRepository.deleteAll();
        aggregateId = UUID.randomUUID();
    }

    @Test
    void shouldCreateAndPersistPendingOutboxEvent() {
        doNothing().when(eventPublisher).publish(anyString(), anyString(), anyString(), anyString(), any());

        OutboxEvent event = outboxEventService.createEvent(
                "billing.payment.succeeded",
                "Payment",
                aggregateId,
                Map.of("paymentId", UUID.randomUUID().toString(), "amount", "29.90"));

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(event.getId()))
                .get()
                .extracting(OutboxEvent::getStatus)
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void shouldMarkEventAsPublished() {
        OutboxEvent event = outboxEventService.createEvent("billing.payment.succeeded",
                "Payment", aggregateId, Map.of("paymentId", UUID.randomUUID().toString()));

        outboxEventService.markAsPublished(event.getId());

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(updated.getProcessedAt()).isNotNull();
    }

    @Test
    void shouldMarkEventAsFailedAndIncrementRetry() {
        OutboxEvent event = outboxEventService.createEvent("billing.payment.failed",
                "Payment", aggregateId, Map.of("paymentId", UUID.randomUUID().toString()));

        outboxEventService.markAsFailed(event.getId(), "sqs unavailable");

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(updated.getLastError()).isEqualTo("sqs unavailable");
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    void shouldIncrementRetryCountWithoutChangingStatus() {
        OutboxEvent event = outboxEventService.createEvent("billing.payment.succeeded",
                "Payment", aggregateId, Map.of());

        outboxEventService.incrementRetryCount(event.getId());

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void shouldDeleteOldPublishedEventsOnly() {
        Instant now = Instant.now();

        OutboxEvent old = OutboxEvent.builder()
                .id(UUID.randomUUID()).eventId(UUID.randomUUID())
                .aggregateType("Payment").aggregateId(aggregateId)
                .eventType("billing.payment.succeeded")
                .eventPayload(Map.of())
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(Duration.ofDays(10)))
                .build();
        OutboxEvent recent = OutboxEvent.builder()
                .id(UUID.randomUUID()).eventId(UUID.randomUUID())
                .aggregateType("Payment").aggregateId(aggregateId)
                .eventType("billing.payment.succeeded")
                .eventPayload(Map.of())
                .status(OutboxEventStatus.PUBLISHED)
                .processedAt(now.minus(Duration.ofDays(3)))
                .build();
        OutboxEvent pending = OutboxEvent.builder()
                .id(UUID.randomUUID()).eventId(UUID.randomUUID())
                .aggregateType("Payment").aggregateId(aggregateId)
                .eventType("billing.payment.succeeded")
                .eventPayload(Map.of())
                .status(OutboxEventStatus.PENDING)
                .build();

        outboxEventJpaRepository.save(old);
        outboxEventJpaRepository.save(recent);
        outboxEventJpaRepository.save(pending);

        int deleted = outboxEventRepository.deleteOldPublishedEvents(now.minus(Duration.ofDays(7)));

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxEventRepository.findById(old.getId())).isEmpty();
        assertThat(outboxEventRepository.findById(recent.getId())).isPresent();
        assertThat(outboxEventRepository.findById(pending.getId())).isPresent();
    }

    @Test
    void shouldReturnOnlyPendingEvents() {
        OutboxEvent pending1 = outboxEventService.createEvent("billing.payment.succeeded",
                "Payment", aggregateId, Map.of("n", "1"));
        outboxEventService.createEvent("billing.payment.succeeded",
                "Payment", UUID.randomUUID(), Map.of("n", "2"));
        OutboxEvent published = outboxEventService.createEvent("billing.payment.succeeded",
                "Payment", UUID.randomUUID(), Map.of("n", "3"));
        outboxEventService.markAsPublished(published.getId());

        var pending = outboxEventService.getPendingEvents();
        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(e -> e.getStatus() == OutboxEventStatus.PENDING);
        assertThat(pending).anyMatch(e -> e.getId().equals(pending1.getId()));
    }

    @Test
    void shouldReturnEventsByAggregate() {
        UUID agg1 = UUID.randomUUID();
        UUID agg2 = UUID.randomUUID();

        outboxEventService.createEvent("billing.payment.succeeded", "Payment", agg1, Map.of());
        outboxEventService.createEvent("billing.payment.failed", "Payment", agg1, Map.of());
        outboxEventService.createEvent("billing.payment.succeeded", "Payment", agg2, Map.of());

        var events = outboxEventService.getEventsByAggregate("Payment", agg1);
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.getAggregateId().equals(agg1));
    }
}
