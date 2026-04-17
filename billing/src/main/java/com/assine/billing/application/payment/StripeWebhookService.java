package com.assine.billing.application.payment;

import com.assine.billing.application.customer.BillingSubscriptionService;
import com.assine.billing.application.outbox.OutboxEventService;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Translates Stripe webhook events into billing-side state changes and outbox events.
 *
 * <p>Responsible for: locating the local {@link Payment} by provider reference
 * (or by {@code metadata.paymentId} as a fallback), flipping its status, marking
 * the attached {@link BillingSubscription} as active/canceled and publishing the
 * corresponding {@code billing.*} events.
 *
 * <p>Idempotency for the webhook envelope itself is enforced upstream by
 * {@link com.assine.billing.adapters.inbound.rest.webhook.StripeWebhookController}
 * via the {@code processed_events} dedup table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusUpdateService paymentStatusUpdateService;
    private final BillingSubscriptionService billingSubscriptionService;
    private final OutboxEventService outboxEventService;

    /**
     * Handle {@code payment_intent.succeeded}.
     *
     * @param providerPaymentRef the Stripe PaymentIntent id (pi_...).
     * @param metadataPaymentId  optional UUID carried in {@code metadata.paymentId}; used
     *                           when the local row was not yet linked to the provider ref.
     */
    @Transactional
    public void handlePaymentSucceeded(String providerPaymentRef, String metadataPaymentId) {
        Optional<Payment> maybe = locate(providerPaymentRef, metadataPaymentId);
        if (maybe.isEmpty()) {
            log.warn("payment_intent.succeeded: payment not found for providerRef={} metadataId={}",
                    providerPaymentRef, metadataPaymentId);
            return;
        }
        Payment payment = maybe.get();

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.info("payment_intent.succeeded already applied for paymentId={}, skipping", payment.getId());
            return;
        }

        paymentStatusUpdateService.execute(payment.getId(), PaymentStatus.SUCCEEDED, null);

        publishSucceeded(payment);

        BillingSubscription subscription = payment.getSubscription();
        if (subscription != null) {
            billingSubscriptionService.markActive(subscription);
            publishActivated(subscription);
        }
    }

    /**
     * Handle {@code payment_intent.payment_failed}.
     */
    @Transactional
    public void handlePaymentFailed(String providerPaymentRef, String metadataPaymentId, String reason) {
        Optional<Payment> maybe = locate(providerPaymentRef, metadataPaymentId);
        if (maybe.isEmpty()) {
            log.warn("payment_intent.payment_failed: payment not found for providerRef={} metadataId={}",
                    providerPaymentRef, metadataPaymentId);
            return;
        }
        Payment payment = maybe.get();

        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("payment_intent.payment_failed already applied for paymentId={}, skipping", payment.getId());
            return;
        }

        paymentStatusUpdateService.execute(payment.getId(), PaymentStatus.FAILED, reason);

        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId().toString());
        if (payment.getSubscription() != null) {
            payload.put("subscriptionId", payment.getSubscription().getSubscriptionId().toString());
        }
        payload.put("attempts", 1);
        payload.put("reason", reason);
        outboxEventService.createEvent("billing.payment.failed", "Payment", payment.getId(), payload);
    }

    /**
     * Handle {@code charge.refunded}. Sets the payment to {@link PaymentStatus#REFUNDED}.
     */
    @Transactional
    public void handleChargeRefunded(String providerPaymentRef, String metadataPaymentId) {
        Optional<Payment> maybe = locate(providerPaymentRef, metadataPaymentId);
        if (maybe.isEmpty()) {
            log.warn("charge.refunded: payment not found for providerRef={} metadataId={}",
                    providerPaymentRef, metadataPaymentId);
            return;
        }
        Payment payment = maybe.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        paymentStatusUpdateService.execute(payment.getId(), PaymentStatus.REFUNDED, null);
    }

    private Optional<Payment> locate(String providerPaymentRef, String metadataPaymentId) {
        if (providerPaymentRef != null && !providerPaymentRef.isBlank()) {
            Optional<Payment> byRef = paymentRepository.findByProviderPaymentRef(providerPaymentRef);
            if (byRef.isPresent()) return byRef;
        }
        if (metadataPaymentId != null && !metadataPaymentId.isBlank()) {
            try {
                return paymentRepository.findById(java.util.UUID.fromString(metadataPaymentId));
            } catch (IllegalArgumentException ignored) {
                log.warn("Invalid metadata.paymentId, cannot locate payment: {}", metadataPaymentId);
            }
        }
        return Optional.empty();
    }

    private void publishSucceeded(Payment payment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId().toString());
        payload.put("customerId", payment.getCustomer().getId().toString());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("providerPaymentRef", payment.getProviderPaymentRef());

        BillingSubscription subscription = payment.getSubscription();
        if (subscription != null) {
            payload.put("subscriptionId", subscription.getSubscriptionId().toString());
            payload.put("currentPeriodStart", toStringOrNull(subscription.getCurrentPeriodStart()));
            payload.put("currentPeriodEnd", toStringOrNull(subscription.getCurrentPeriodEnd()));
        }
        outboxEventService.createEvent("billing.payment.succeeded", "Payment", payment.getId(), payload);
    }

    private void publishActivated(BillingSubscription subscription) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", subscription.getSubscriptionId().toString());
        payload.put("currentPeriodStart", toStringOrNull(subscription.getCurrentPeriodStart()));
        payload.put("currentPeriodEnd", toStringOrNull(subscription.getCurrentPeriodEnd()));
        payload.put("billingRef", subscription.getProviderSubscriptionRef());
        outboxEventService.createEvent("billing.subscription.activated", "Subscription",
                subscription.getSubscriptionId(), payload);
    }

    private static String toStringOrNull(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
