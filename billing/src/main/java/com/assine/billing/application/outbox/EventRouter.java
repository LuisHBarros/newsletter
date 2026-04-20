package com.assine.billing.application.outbox;

import com.assine.billing.application.customer.BillingCustomerService;
import com.assine.billing.application.customer.BillingSubscriptionService;
import com.assine.billing.application.payment.provider.PaymentProviderPort;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.repository.BillingSubscriptionRepository;
import com.assine.billing.domain.outbox.port.EventConsumer;
import com.assine.billing.domain.plan.model.BillingPlan;
import com.assine.billing.domain.plan.repository.BillingPlanRepository;
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
 * {@code subscription.requested} triggers provisioning + Stripe subscription creation;
 * {@code subscription.cancel_requested} triggers cancellation.
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
    private final OutboxEventService outboxEventService;
    private final BillingPlanRepository planRepository;
    private final PaymentProviderPort paymentProviderPort;

    private Map<String, BiConsumer<Map<String, Object>, UUID>> handlers() {
        return Map.of(
            "subscription.requested", this::handleSubscriptionRequested,
            "subscription.cancel_requested", this::handleSubscriptionCancelRequested,
            "plan.created", this::handlePlanCreated,
            "plan.updated", this::handlePlanUpdated,
            "plan.deleted", this::handlePlanDeleted
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
        String userEmail = (String) payload.get("userEmail");
        String userName = (String) payload.get("userName");
        UUID planId = UUID.fromString((String) payload.get("planId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) payload.get("planSnapshot");
        String billingInterval = (String) snapshot.get("billingInterval");
        String stripePriceId = (String) snapshot.get("stripePriceId");

        BillingCustomer customer = customerService.findOrCreate(userId, userEmail, userName);
        BillingSubscription subscription = billingSubscriptionService.findOrCreate(
            subscriptionId, customer, planId, billingInterval);

        // TRIAL handling: status=TRIAL in snapshot means skip charge, activate immediately.
        String status = (String) payload.get("status");
        if ("TRIAL".equals(status)) {
            int trialDays = toInteger(snapshot.get("trialDays"));
            billingSubscriptionService.markTrialActive(subscription, trialDays);
            publishActivated(subscription);
            return;
        }

        // Stripe Subscription flow: create subscription with DEFAULT_INCOMPLETE payment behavior.
        // If stripePriceId is null and we're not in Stripe mode, fall back to fake reference.
        try {
            String providerSubscriptionRef = paymentProviderPort.createSubscription(
                customer.getId(), stripePriceId, subscriptionId, eventId.toString());

            // Save the real Stripe subscription reference
            subscription.setProviderSubscriptionRef(providerSubscriptionRef);
            billingSubscriptionRepository.save(subscription);

            log.info("Subscription created with provider: subscriptionId={} providerRef={}",
                subscriptionId, providerSubscriptionRef);

            // Note: Subscription activation happens via webhook (invoice.payment_succeeded)
            // NOT immediately here. The subscription remains PENDING until payment confirmation.
        } catch (RuntimeException e) {
            log.warn("Subscription creation failed for subscriptionId={} reason={}", subscriptionId, e.getMessage());
            // For backward compatibility / dev mode without stripePriceId, we could fall back
            // to the old flow, but prefer explicit failure for now.
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

        String providerRef = subscription.getProviderSubscriptionRef();
        if (providerRef != null && !providerRef.isBlank()) {
            paymentProviderPort.cancelSubscription(providerRef);
        } else {
            log.warn("No providerSubscriptionRef for subscriptionId={}, skipping provider cancel", subscriptionId);
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

    private void handlePlanCreated(Map<String, Object> payload, UUID eventId) {
        UUID planId = UUID.fromString((String) payload.get("planId"));
        BillingPlan plan = BillingPlan.builder()
            .id(UUID.randomUUID())
            .planId(planId)
            .name((String) payload.get("name"))
            .price(toBigDecimal(payload.get("price")))
            .currency((String) payload.get("currency"))
            .billingInterval((String) payload.get("billingInterval"))
            .trialDays(toInteger(payload.get("trialDays")))
            .active(toBoolean(payload.get("active")))
            .build();
        planRepository.save(plan);
        log.info("Created billing plan: planId={}", planId);
    }

    private void handlePlanUpdated(Map<String, Object> payload, UUID eventId) {
        UUID planId = UUID.fromString((String) payload.get("planId"));
        BillingPlan plan = planRepository.findByPlanId(planId)
            .orElseThrow(() -> new IllegalArgumentException("BillingPlan not found: " + planId));
        plan.setName((String) payload.get("name"));
        plan.setPrice(toBigDecimal(payload.get("price")));
        plan.setCurrency((String) payload.get("currency"));
        plan.setBillingInterval((String) payload.get("billingInterval"));
        plan.setTrialDays(toInteger(payload.get("trialDays")));
        plan.setActive(toBoolean(payload.get("active")));
        planRepository.save(plan);
        log.info("Updated billing plan: planId={}", planId);
    }

    private void handlePlanDeleted(Map<String, Object> payload, UUID eventId) {
        UUID planId = UUID.fromString((String) payload.get("planId"));
        planRepository.deleteByPlanId(planId);
        log.info("Deleted billing plan: planId={}", planId);
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

    private Integer toInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private Boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
