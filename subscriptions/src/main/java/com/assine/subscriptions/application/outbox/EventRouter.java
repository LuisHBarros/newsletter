package com.assine.subscriptions.application.outbox;

import com.assine.subscriptions.application.subscription.SubscriptionService;
import com.assine.subscriptions.domain.outbox.port.EventConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Routes inbound events (published by other services, notably {@code billing}) to domain
 * transitions in {@link SubscriptionService}. Events authored by this service are intentionally
 * not routed back here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRouter implements EventConsumer {

    private final SubscriptionService subscriptionService;

    private Map<String, Consumer<Map<String, Object>>> handlers() {
        return Map.of(
            "billing.subscription.activated", this::handleBillingActivated,
            "billing.payment.succeeded", this::handlePaymentSucceeded,
            "billing.payment.failed", this::handlePaymentFailed,
            "billing.subscription.canceled", this::handleBillingCanceled
        );
    }

    @Override
    public void consume(String eventType, Map<String, Object> payload) {
        Consumer<Map<String, Object>> handler = handlers().get(eventType);

        if (handler != null) {
            handler.accept(payload);
        } else {
            log.warn("No handler found for event type: {}", eventType);
        }
    }

    private void handleBillingActivated(Map<String, Object> payload) {
        log.info("Handling billing.subscription.activated: {}", payload);
        UUID subscriptionId = UUID.fromString((String) payload.get("subscriptionId"));
        Instant start = parseInstant(payload.get("currentPeriodStart"));
        Instant end = parseInstant(payload.get("currentPeriodEnd"));
        subscriptionService.activate(subscriptionId, start, end);
    }

    private void handlePaymentSucceeded(Map<String, Object> payload) {
        log.info("Handling billing.payment.succeeded: {}", payload);
        UUID subscriptionId = UUID.fromString((String) payload.get("subscriptionId"));
        Instant periodStart = parseInstant(payload.get("currentPeriodStart"));
        Instant periodEnd = parseInstant(payload.get("currentPeriodEnd"));
        subscriptionService.renewPeriod(subscriptionId, periodStart, periodEnd);
    }

    private void handlePaymentFailed(Map<String, Object> payload) {
        log.info("Handling billing.payment.failed: {}", payload);
        UUID subscriptionId = UUID.fromString((String) payload.get("subscriptionId"));
        subscriptionService.markPastDue(subscriptionId);
    }

    private void handleBillingCanceled(Map<String, Object> payload) {
        log.info("Handling billing.subscription.canceled: {}", payload);
        UUID subscriptionId = UUID.fromString((String) payload.get("subscriptionId"));
        Instant canceledAt = parseInstant(payload.get("canceledAt"));
        String reason = (String) payload.get("reason");
        subscriptionService.confirmCanceled(subscriptionId, canceledAt, reason);
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        String str = value.toString();
        return str.isEmpty() ? null : Instant.parse(str);
    }
}
