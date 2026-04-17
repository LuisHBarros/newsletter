package com.assine.billing.adapters.outbound.messaging.sqs;

import com.assine.billing.application.outbox.EventRouter;
import com.assine.billing.domain.outbox.model.ProcessedEvent;
import com.assine.billing.domain.outbox.repository.ProcessedEventRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes events from the billing inbound queue (subscriptions -> billing).
 * Dedup via {@code processed_events} unique constraint — same pattern as subscriptions.
 * Differs from the subscriptions service only in the target queue and in passing
 * {@code eventId} to the router (used as provider idempotency-key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsEventConsumer {

    private final EventRouter eventRouter;
    private final ProcessedEventRepository processedEventRepository;

    @SqsListener(queueNames = "${aws.sqs.inbound.queue:assine-billing}")
    @Transactional
    public void consumeMessage(
            Map<String, Object> message,
            @Header(value = "id", required = false) String messageId,
            @Header(value = "ApproximateReceiveCount", required = false) String approximateReceiveCount) {
        try {
            String eventType = (String) message.get("eventType");
            UUID eventId = extractEventId(message, messageId);

            log.info("Received message from SQS: messageId={}, eventId={}, receiveCount={}, eventType={}",
                    messageId, eventId, approximateReceiveCount, eventType);

            // Atomic dedup
            try {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .build());
            } catch (DataIntegrityViolationException dup) {
                log.info("Duplicate event detected, skipping: eventId={}, messageId={}", eventId, messageId);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");

            eventRouter.consume(eventType, payload, eventId);

            log.info("Processed event: messageId={}, eventType={}, eventId={}", messageId, eventType, eventId);
        } catch (Exception e) {
            log.error("Failed to process message from SQS: messageId={}, receiveCount={}, eventType={}",
                    messageId, approximateReceiveCount, message.get("eventType"), e);
            throw e;
        }
    }

    private UUID extractEventId(Map<String, Object> message, String messageId) {
        String eventIdStr = (String) message.get("eventId");
        if (eventIdStr != null && !eventIdStr.isEmpty()) {
            try {
                return UUID.fromString(eventIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid eventId in envelope ({}), falling back", eventIdStr);
            }
        }
        if (messageId != null) {
            try {
                return UUID.fromString(messageId);
            } catch (IllegalArgumentException ignored) {
                // not a UUID
            }
        }
        return UUID.randomUUID();
    }
}
