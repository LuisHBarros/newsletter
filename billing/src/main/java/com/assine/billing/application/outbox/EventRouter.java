package com.assine.billing.application.outbox;

import com.assine.billing.application.customer.BillingCustomerService;
import com.assine.billing.application.customer.BillingSubscriptionService;
import com.assine.billing.application.payment.CreatePaymentService;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.repository.BillingSubscriptionRepository;
import com.assine.billing.domain.outbox.port.EventConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Routes inbound events (published by {@code subscriptions}) to billing-side actions:
 * {@code subscription.requested} triggers provisioning + charge; {@code subscription.cancel_requested}
 * triggers cancellation. Replaces the Kafka-based {@code TransferStatusConsumer} from the
 * original payment-service.
 *
 * <p>Receives {@code eventId} from the SQS consumer so it can be used as an idempotency-key
 * toward the payment provider.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRouter implements EventConsumer {

    private final BillingCustomerService customerService;
    private final BillingSubscriptionService billingSubscriptionService;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final CreatePaymentService createPaymentService;
    private final OutboxEventService outboxEventService;

    private Map<String, BiConsumer<Map<String, Object>, UUID>> handlers() {
        return Map.of(
            "subscription.requested", this::handleSubscriptionRequested,
            "subscription.cancel_requested", this::handleSubscriptionCancelRequested
        );
    }

    @Override
    public void consume(String eventType, Map<String, Object> payload) {
        // Back-compat with EventConsumer port: when no eventId is available, generate one.
        consume(eventType, payload, UUID.randomUUID());
    }

    public void consume(String eventType, Map<String, Object> payload, UUID eventId) {
        BiConsumer<Map<String, Object>, UUID> handler = handlers().get(eventType);
        if (handler == null) {
            log.warn("No handler for event type: {}", eventType);
            return;
        }
        handler.accept(payload, eventId);
    }

    private void handleSubscriptionRequested(Map<String, Object> payload, UUID eventId) {
        log.info("Handling subscription.requested: {}", payload);

        UUID subscriptionId = UUID.fromString((String) payload.get("subscriptionId"));
        UUID userId = UUID.fromString((String) payload.get("userId"));
        UUID planId = UUID.fromString((String) payload.get("planId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) payload.get("planSnapshot");
        BigDecimal price = toBigDecimal(snapshot.get("price"));
        String currency = (String) snapshot.get("currency");
        String billingInterval = (String) snapshot.get("billingInterval");

        BillingCustomer customer = customerService.findOrCreate(userId);
        BillingSubscription subscription = billingSubscriptionService.findOrCreate(
            subscriptionId, customer, planId, billingInterval);

        // TRIAL handling: status=TRIAL in snapshot means skip charge, activate immediately.
        String status = (String) payload.get("status");
        if ("TRIAL".equals(status)) {
            billingSubscriptionService.markActive(subscription);
            publishActivated(subscription);
            return;
        }

        // Async flow: CreatePaymentService only registers the PaymentIntent (PENDING).
        // billing.payment.succeeded + billing.subscription.activated are emitted later by
        // StripeWebhookService when payment_intent.succeeded arrives. On synchronous
        // provider-creation errors, billing.payment.failed is already emitted by
        // CreatePaymentService before the exception escapes.
        try {
            createPaymentService.execute(customer.getId(), subscription, price, currency, eventId.toString());
        } catch (RuntimeException e) {
            log.warn("Charge registration failed for subscriptionId={} reason={}", subscriptionId, e.getMessage());
        }
    }

    private void handleSubscriptionCancelRequested(Map<String, Object> payload, UUID eventId) {
        log.info("Handling subscription.cancel_requested: {}", payload);

        UUID subscriptionId = UUID.fromString((String) payload.get("subscriptionId"));
        BillingSubscription subscription = billingSubscriptionRepository.findBySubscriptionId(subscriptionId)
            .orElse(null);

        if (subscription == null) {
            log.warn("No BillingSubscription found for subscriptionId={}, ignoring cancel", subscriptionId);
            return;
        }

        Instant canceledAt = Instant.now();
        billingSubscriptionService.markCanceled(subscription, canceledAt);

        Map<String, Object> canceledPayload = new HashMap<>();
        canceledPayload.put("subscriptionId", subscriptionId.toString());
        canceledPayload.put("canceledAt", canceledAt.toString());
        canceledPayload.put("reason", "user_requested");
        outboxEventService.createEvent("billing.subscription.canceled", "Subscription",
            subscriptionId, canceledPayload);
    }

    private void publishActivated(BillingSubscription subscription) {
        Map<String, Object> activatedPayload = new HashMap<>();
        activatedPayload.put("subscriptionId", subscription.getSubscriptionId().toString());
        activatedPayload.put("currentPeriodStart", toStringOrNull(subscription.getCurrentPeriodStart()));
        activatedPayload.put("currentPeriodEnd", toStringOrNull(subscription.getCurrentPeriodEnd()));
        activatedPayload.put("billingRef", subscription.getProviderSubscriptionRef());
        outboxEventService.createEvent("billing.subscription.activated", "Subscription",
            subscription.getSubscriptionId(), activatedPayload);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }

    private static String toStringOrNull(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
