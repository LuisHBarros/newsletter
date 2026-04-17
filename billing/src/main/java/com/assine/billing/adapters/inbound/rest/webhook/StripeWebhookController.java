package com.assine.billing.adapters.inbound.rest.webhook;

import com.assine.billing.application.payment.StripeWebhookService;
import com.assine.billing.config.StripeProperties;
import com.assine.billing.domain.outbox.model.ProcessedEvent;
import com.assine.billing.domain.outbox.repository.ProcessedEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Stripe webhook endpoint.
 *
 * <p>Verifies the {@code Stripe-Signature} header against the configured
 * {@code billing.stripe.webhookSecret}; only then deserializes the event and
 * routes it to {@link StripeWebhookService}. Duplicates (same {@code event.id})
 * are dedup'd via the shared {@code processed_events} table.
 *
 * <p>Returns {@code 200} (and logs) for unknown event types so Stripe doesn't
 * retry them forever, and {@code 400} for signature-verification failures.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeProperties properties;
    private final StripeWebhookService webhookService;
    private final ProcessedEventRepository processedEventRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (!properties.enabled()) {
            log.warn("Stripe webhook received but integration is disabled");
            return ResponseEntity.status(503).body(Map.of("error", "stripe disabled"));
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing Stripe-Signature header"));
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, properties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid signature"));
        }

        UUID eventUuid = deterministicUuid(event.getId());
        try {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventUuid)
                    .eventType("stripe:" + event.getType())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate Stripe webhook skipped: id={} type={}", event.getId(), event.getType());
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        dispatch(event);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void dispatch(Event event) {
        String type = event.getType();
        StripeObject dataObject = resolveDataObject(event);

        switch (type) {
            case "payment_intent.succeeded" -> {
                if (dataObject instanceof PaymentIntent intent) {
                    webhookService.handlePaymentSucceeded(
                            intent.getId(),
                            intent.getMetadata() != null ? intent.getMetadata().get("paymentId") : null);
                } else {
                    log.warn("payment_intent.succeeded without PaymentIntent object (id={})", event.getId());
                }
            }
            case "payment_intent.payment_failed" -> {
                if (dataObject instanceof PaymentIntent intent) {
                    String reason = intent.getLastPaymentError() != null
                            ? intent.getLastPaymentError().getMessage()
                            : "payment_intent.payment_failed";
                    webhookService.handlePaymentFailed(
                            intent.getId(),
                            intent.getMetadata() != null ? intent.getMetadata().get("paymentId") : null,
                            reason);
                } else {
                    log.warn("payment_intent.payment_failed without PaymentIntent object (id={})", event.getId());
                }
            }
            case "charge.refunded" -> {
                // Charge carries paymentIntent id; for simplicity we pull from event raw json via metadata fallback.
                String pi = null;
                String metaPaymentId = null;
                if (dataObject instanceof com.stripe.model.Charge charge) {
                    pi = charge.getPaymentIntent();
                    if (charge.getMetadata() != null) {
                        metaPaymentId = charge.getMetadata().get("paymentId");
                    }
                }
                webhookService.handleChargeRefunded(pi, metaPaymentId);
            }
            default -> log.info("Ignoring Stripe event type: {} (id={})", type, event.getId());
        }
    }

    /**
     * Resolve the {@code data.object} from the event envelope. Stripe's
     * {@link EventDataObjectDeserializer#getObject()} returns empty when the
     * library's bundled API version doesn't match {@code event.api_version};
     * in that case we fall back to {@link EventDataObjectDeserializer#deserializeUnsafe()}
     * which re-parses against the library's schema. This is the standard
     * recipe for webhook ingestion — our PaymentIntent fields are stable across
     * minor API bumps.
     */
    private StripeObject resolveDataObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.warn("Failed to deserialize Stripe event data.object (id={} type={}): {}",
                        event.getId(), event.getType(), e.getMessage());
                return null;
            }
        });
    }

    /**
     * Stripe event ids are opaque strings ({@code evt_...}); the dedup table uses UUIDs,
     * so we fold the event id into a deterministic UUID (name-based, namespace-agnostic).
     */
    static UUID deterministicUuid(String stripeEventId) {
        return UUID.nameUUIDFromBytes(("stripe:" + stripeEventId).getBytes());
    }
}
