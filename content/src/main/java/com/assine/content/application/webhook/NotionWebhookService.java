package com.assine.content.application.webhook;

import com.assine.content.adapters.outbound.messaging.sqs.SqsContentJobsPublisher;
import com.assine.content.domain.webhook.model.NotionWebhookDelivery;
import com.assine.content.domain.webhook.repository.NotionWebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles incoming Notion webhooks: records the delivery (dedupe by {@code deliveryId}),
 * validates HMAC (already verified by the controller/filter), and enqueues an
 * {@link ImportIssueJob} on the content-jobs SQS queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionWebhookService {

    private final NotionWebhookDeliveryRepository deliveryRepository;
    private final SqsContentJobsPublisher jobsPublisher;

    public enum Outcome { ACCEPTED, DUPLICATE, INVALID }

    @Transactional
    public Outcome handle(String deliveryId, Map<String, Object> payload) {
        String effectiveDeliveryId = deliveryId != null && !deliveryId.isBlank()
                ? deliveryId
                : UUID.randomUUID().toString();

        if (deliveryRepository.findByDeliveryId(effectiveDeliveryId).isPresent()) {
            log.info("Duplicate Notion webhook deliveryId={}", effectiveDeliveryId);
            return Outcome.DUPLICATE;
        }

        NotionWebhookDelivery delivery = deliveryRepository.save(NotionWebhookDelivery.builder()
                .id(UUID.randomUUID())
                .deliveryId(effectiveDeliveryId)
                .payload(payload != null ? payload : Map.of())
                .status(NotionWebhookDelivery.Status.RECEIVED)
                .build());

        String pageId = extractPageId(payload);
        Instant lastEditedAt = extractLastEditedAt(payload);

        if (pageId == null) {
            deliveryRepository.markProcessed(delivery.getId(),
                    NotionWebhookDelivery.Status.REJECTED, "payload missing page id");
            log.warn("Webhook {} rejected: missing page id", effectiveDeliveryId);
            return Outcome.INVALID;
        }

        try {
            jobsPublisher.enqueueImport(new ImportIssueJob(pageId, lastEditedAt, "webhook"));
            deliveryRepository.markProcessed(delivery.getId(), NotionWebhookDelivery.Status.ACCEPTED, null);
            return Outcome.ACCEPTED;
        } catch (Exception e) {
            deliveryRepository.markProcessed(delivery.getId(), NotionWebhookDelivery.Status.FAILED, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractPageId(Map<String, Object> payload) {
        if (payload == null) return null;
        Object direct = payload.get("page_id");
        if (direct instanceof String s) return s;
        Object entity = payload.get("entity");
        if (entity instanceof Map<?, ?> m && m.get("id") instanceof String s) return s;
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof String s) return s;
            Object page = m.get("page");
            if (page instanceof Map<?, ?> p && p.get("id") instanceof String s) return s;
        }
        return null;
    }

    private Instant extractLastEditedAt(Map<String, Object> payload) {
        if (payload == null) return null;
        Object v = payload.get("last_edited_time");
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }
}
