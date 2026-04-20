package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPublisherService publisherService;

    @Mock
    private MeterRegistry meterRegistry;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, publisherService, meterRegistry);
    }

    @Test
    void drain_noPendingEvents_shouldDoNothing() {
        // Given
        when(outboxEventRepository.findDueForPublishing(eq(50), any(Instant.class))).thenReturn(Collections.emptyList());

        // When
        outboxRelay.drain();

        // Then
        verify(outboxEventRepository).findDueForPublishing(eq(50), any(Instant.class));
        verifyNoInteractions(publisherService);
    }

    @Test
    void drain_withPendingEvents_shouldDelegateToPublisherService() {
        // Given
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        OutboxEvent event1 = OutboxEvent.builder()
                .id(eventId1)
                .eventId(UUID.randomUUID())
                .eventType("event.one")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(eventId2)
                .eventId(UUID.randomUUID())
                .eventType("event.two")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(1)
                .build();

        when(outboxEventRepository.findDueForPublishing(eq(50), any(Instant.class)))
                .thenReturn(List.of(event1, event2));

        // When
        outboxRelay.drain();

        // Then
        verify(outboxEventRepository).findDueForPublishing(eq(50), any(Instant.class));
        verify(publisherService).publishWithRetry(event1);
        verify(publisherService).publishWithRetry(event2);
        verifyNoMoreInteractions(publisherService);
    }

    @Test
    void drain_publisherServiceThrows_propagatesException() {
        // Given
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        OutboxEvent event1 = OutboxEvent.builder()
                .id(eventId1)
                .eventId(UUID.randomUUID())
                .eventType("event.one")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(eventId2)
                .eventId(UUID.randomUUID())
                .eventType("event.two")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.findDueForPublishing(eq(50), any(Instant.class)))
                .thenReturn(List.of(event1, event2));

        // First event throws
        doThrow(new RuntimeException("Publish failed")).when(publisherService).publishWithRetry(event1);

        // When / Then - exception propagates, only first event is attempted
        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> outboxRelay.drain()
        );

        assertThat(exception.getMessage()).isEqualTo("Publish failed");
        verify(publisherService).publishWithRetry(event1);
        // Second event is never attempted due to exception
        verify(publisherService, never()).publishWithRetry(event2);
    }
}
