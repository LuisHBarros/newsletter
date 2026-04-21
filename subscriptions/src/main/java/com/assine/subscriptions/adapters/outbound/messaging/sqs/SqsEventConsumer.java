package com.assine.subscriptions.adapters.outbound.messaging.sqs;

import com.assine.subscriptions.domain.outbox.model.ProcessedEvent;
import com.assine.subscriptions.domain.outbox.port.EventConsumer;
import com.assine.subscriptions.domain.outbox.repository.ProcessedEventRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SqsEventConsumer {

    private final EventConsumer eventConsumer;
    private final ProcessedEventRepository processedEventRepository;

    @SqsListener(queueNames = "${aws.sqs.subscriptions.queue:assine-subscriptions.fifo}")
    @Transactional
    public void consumeMessage(
            Map<String, Object> message,
            @Header(value = "id", required = false) String messageId,
            @Header(value = "ApproximateReceiveCount", required = false) String approximateReceiveCount) {
        try {
            log.info("Received message from SQS: messageId={}, receiveCount={}, eventType={}",
                    messageId, approximateReceiveCount, message.get("eventType"));

            String eventType = (String) message.get("eventType");
            UUID eventId = extractEventId(message, messageId);

            // Atomic dedup: insert BEFORE handling so both happen in the same
            // transaction. A duplicate triggers DataIntegrityViolationException
            // from the UNIQUE constraint and we skip without re-running the effect.
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

            eventConsumer.consume(eventType, payload);

            log.info("Successfully processed event: messageId={}, eventType={}, eventId={}",
                    messageId, eventType, eventId);
        } catch (Exception e) {
            log.error("Failed to process message from SQS: messageId={}, receiveCount={}, eventType={}",
                    messageId, approximateReceiveCount, message.get("eventType"), e);
            throw e; // Re-throw to trigger SQS retry/DLQ (and rollback dedup row)
        }
    }

    private UUID extractEventId(Map<String, Object> message, String messageId) {
        String eventIdStr = (String) message.get("eventId");
        if (eventIdStr != null && !eventIdStr.isEmpty()) {
            try {
                return UUID.fromString(eventIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid eventId in envelope ({}), falling back to random UUID", eventIdStr);
            }
        }
        // No eventId in envelope: producer didn't follow the contract.
        // Use messageId if it is a valid UUID; otherwise generate one so we never
        // try to parse a non-UUID string into UUID.fromString().
        if (messageId != null) {
            try {
                return UUID.fromString(messageId);
            } catch (IllegalArgumentException ignored) {
                // not a UUID, fall through
            }
        }
        return UUID.randomUUID();
    }
}
