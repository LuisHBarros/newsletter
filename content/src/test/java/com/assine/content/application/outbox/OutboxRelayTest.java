package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPublisherService publisherService;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, publisherService);
    }

    @Test
    void drain_noPendingEvents_shouldDoNothing() {
        // Given
        when(outboxEventRepository.findDueForPublishing(50, Instant.now())).thenReturn(Collections.emptyList());

        // When
        outboxRelay.drain();

        // Then
        verify(outboxEventRepository).findDueForPublishing(50, Instant.now());
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

        when(outboxEventRepository.findDueForPublishing(50, Instant.now()))
                .thenReturn(List.of(event1, event2));

        // When
        outboxRelay.drain();

        // Then
        verify(outboxEventRepository).findDueForPublishing(50, Instant.now());
        verify(publisherService).publishWithRetry(event1);
        verify(publisherService).publishWithRetry(event2);
        verifyNoMoreInteractions(publisherService);
    }

    @Test
    void drain_publisherServiceThrows_shouldContinueWithNextEvent() {
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

        when(outboxEventRepository.findDueForPublishing(50, Instant.now()))
                .thenReturn(List.of(event1, event2));

        // First event throws, second should still be processed
        doThrow(new RuntimeException("Publish failed")).when(publisherService).publishWithRetry(event1);

        // When
        outboxRelay.drain();

        // Then - both events should be attempted
        verify(publisherService).publishWithRetry(event1);
        verify(publisherService).publishWithRetry(event2);
    }
}
