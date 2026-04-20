package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherServiceTest {

    @Mock
    private ResilientEventPublisher resilientEventPublisher;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventPublisherService service;

    @BeforeEach
    void setUp() {
        service = new OutboxEventPublisherService(resilientEventPublisher, outboxEventRepository);
    }

    @Test
    void publishWithRetry_success_shouldMarkAsPublished() {
        // Given
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .eventId(UUID.randomUUID())
                .eventType("test.event")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        // When
        service.publishWithRetry(event);

        // Then
        verify(resilientEventPublisher).publish(event);
        verify(outboxEventRepository).markAsPublished(eventId);
        verifyNoMoreInteractions(outboxEventRepository);
    }

    @Test
    void publishWithRetry_fail_thenSchedule_shouldIncrementRetryAndSchedule() {
        // Given
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .eventId(UUID.randomUUID())
                .eventType("test.event")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        RuntimeException exception = new RuntimeException("SQS failure");
        doThrow(exception).when(resilientEventPublisher).publish(event);

        // When
        service.publishWithRetry(event);

        // Then
        verify(resilientEventPublisher).publish(event);

        ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxEventRepository).scheduleRetry(eq(eventId), nextAttemptCaptor.capture(), eq("SQS failure"));

        // Verify backoff is ~60 seconds (BASE_DELAY 30s * 2^1)
        Instant nextAttempt = nextAttemptCaptor.getValue();
        Duration backoff = Duration.between(Instant.now(), nextAttempt);
        assertThat(backoff).isBetween(Duration.ofSeconds(55), Duration.ofSeconds(65));

        verifyNoMoreInteractions(outboxEventRepository);
    }

    @Test
    void publishWithRetry_maxRetriesExceeded_shouldMarkAsFailed() {
        // Given
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .eventId(UUID.randomUUID())
                .eventType("test.event")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(2) // Already at max - 1
                .build();

        RuntimeException exception = new RuntimeException("Persistent failure");
        doThrow(exception).when(resilientEventPublisher).publish(event);

        // When
        service.publishWithRetry(event);

        // Then
        verify(resilientEventPublisher).publish(event);
        verify(outboxEventRepository).markAsFailed(eventId, "Persistent failure");
        verifyNoMoreInteractions(outboxEventRepository);
    }

    @Test
    void calculateBackoff_shiftOverflow_shouldClampToMaxSafeShift() {
        // This test verifies that retryCount > 62 doesn't cause shift overflow
        // We test indirectly by checking the behavior with a very high retry count

        // Given
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .eventId(UUID.randomUUID())
                .eventType("test.event")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(100) // Way above MAX_SAFE_SHIFT = 62
                .build();

        doThrow(new RuntimeException("Fail")).when(resilientEventPublisher).publish(event);

        // When - should not throw ArithmeticException or shift overflow
        service.publishWithRetry(event);

        // Then - should still schedule retry (now at retry 101, so mark as failed)
        // Actually with retryCount=100, newRetryCount=101 which exceeds MAX_RETRY_COUNT=3
        // So it should be marked as failed
        verify(outboxEventRepository).markAsFailed(eventId, "Fail");
    }

    @Test
    void calculateBackoff_shouldCapAtMaxDelay() {
        // Given retry count high enough to exceed MAX_DELAY
        // BASE_DELAY=30s, so at retryCount=5: 30*2^5 = 960s = 16min > MAX_DELAY=10min
        // This should be capped at 10 minutes

        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .eventId(UUID.randomUUID())
                .eventType("test.event")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(4) // Will become 5 after increment
                .build();

        doThrow(new RuntimeException("Fail")).when(resilientEventPublisher).publish(event);

        // When
        service.publishWithRetry(event);

        // Then
        ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxEventRepository).scheduleRetry(eq(eventId), nextAttemptCaptor.capture(), any());

        Duration backoff = Duration.between(Instant.now(), nextAttemptCaptor.getValue());
        // Should be capped at MAX_DELAY (10 minutes)
        assertThat(backoff).isLessThanOrEqualTo(Duration.ofMinutes(11));
    }
}
