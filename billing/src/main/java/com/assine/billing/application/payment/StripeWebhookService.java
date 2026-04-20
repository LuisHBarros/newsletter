package com.assine.billing.application.payment;

import com.assine.billing.application.customer.BillingSubscriptionService;
import com.assine.billing.application.outbox.OutboxEventService;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import com.assine.billing.domain.outbox.model.ProcessedEvent;
import com.assine.billing.domain.outbox.repository.ProcessedEventRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final UUID STRIPE_NS = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    private final PaymentRepository paymentRepository;
    private final PaymentStatusUpdateService paymentStatusUpdateService;
    private final BillingSubscriptionService billingSubscriptionService;
    private final OutboxEventService outboxEventService;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void processWithDedup(Event event) {
        UUID eventUuid = deterministicUuid(event.getId());
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventUuid)
                .eventType("stripe:" + event.getType())
                .build());

        dispatch(event);
    }

    private void dispatch(Event event) {
        String type = event.getType();
        StripeObject dataObject = resolveDataObject(event);

        switch (type) {
            case "payment_intent.succeeded" -> {
                if (dataObject instanceof PaymentIntent intent) {
                    handlePaymentSucceeded(
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
                    handlePaymentFailed(
                            intent.getId(),
                            intent.getMetadata() != null ? intent.getMetadata().get("paymentId") : null,
                            reason);
                } else {
                    log.warn("payment_intent.payment_failed without PaymentIntent object (id={})", event.getId());
                }
            }
            case "invoice.payment_succeeded" -> {
                if (dataObject instanceof Invoice invoice) {
                    handleInvoicePaymentSucceeded(invoice);
                } else {
                    log.warn("invoice.payment_succeeded without Invoice object (id={})", event.getId());
                }
            }
            case "invoice.payment_failed" -> {
                if (dataObject instanceof Invoice invoice) {
                    handleInvoicePaymentFailed(invoice);
                } else {
                    log.warn("invoice.payment_failed without Invoice object (id={})", event.getId());
                }
            }
            case "customer.subscription.deleted" -> {
                if (dataObject instanceof Subscription sub) {
                    handleSubscriptionDeleted(sub);
                } else {
                    log.warn("customer.subscription.deleted without Subscription object (id={})", event.getId());
                }
            }
            case "charge.refunded" -> {
                String pi = null;
                String metaPaymentId = null;
                if (dataObject instanceof com.stripe.model.Charge charge) {
                    pi = charge.getPaymentIntent();
                    if (charge.getMetadata() != null) {
                        metaPaymentId = charge.getMetadata().get("paymentId");
                    }
                }
                handleChargeRefunded(pi, metaPaymentId);
            }
            default -> log.info("Ignoring Stripe event type: {} (id={})", type, event.getId());
        }
    }

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

    public static UUID deterministicUuid(String stripeEventId) {
        return uuidV5(STRIPE_NS, "stripe:" + stripeEventId);
    }

    private static UUID uuidV5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(toBytes(namespace));
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            return fromBytes(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) bytes[i] = (byte) (msb >>> (8 * (7 - i)));
        for (int i = 8; i < 16; i++) bytes[i] = (byte) (lsb >>> (8 * (15 - i)));
        return bytes;
    }

    private static UUID fromBytes(byte[] bytes) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xff);
        return new UUID(msb, lsb);
    }

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

        BillingSubscription subscription = payment.getSubscription();
        if (subscription != null) {
            billingSubscriptionService.markActive(subscription);
            publishActivated(subscription);
        }

        publishSucceeded(payment);
        // Note: Stripe handles receipts natively; billing.invoice.receipt_needed removed
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

    /**
     * Handle invoice.payment_succeeded from Stripe subscription.
     * Activates the subscription and publishes success events.
     */
    @Transactional
    public void handleInvoicePaymentSucceeded(Invoice invoice) {
        String subscriptionIdStr = invoice.getMetadata() != null
                ? invoice.getMetadata().get("subscriptionId")
                : null;
        if (subscriptionIdStr == null || subscriptionIdStr.isBlank()) {
            log.warn("invoice.payment_succeeded: no subscriptionId in metadata, invoiceId={}", invoice.getId());
            return;
        }

        UUID subscriptionId;
        try {
            subscriptionId = UUID.fromString(subscriptionIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("invoice.payment_succeeded: invalid subscriptionId in metadata: {}", subscriptionIdStr);
            return;
        }

        BillingSubscription subscription = billingSubscriptionService.findBySubscriptionId(subscriptionId)
                .orElse(null);
        if (subscription == null) {
            log.warn("invoice.payment_succeeded: subscription not found for id={}", subscriptionId);
            return;
        }

        billingSubscriptionService.markActive(subscription);
        publishActivated(subscription);

        // Publish billing.payment.succeeded event
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", subscriptionId.toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("invoiceId", invoice.getId());
        payload.put("amount", invoice.getAmountPaid());
        payload.put("currency", invoice.getCurrency());
        payload.put("providerInvoiceRef", invoice.getId());
        outboxEventService.createEvent("billing.payment.succeeded", "Payment",
                subscriptionId, payload);

        log.info("invoice.payment_succeeded processed: subscriptionId={} invoiceId={}", subscriptionId, invoice.getId());
    }

    /**
     * Handle invoice.payment_failed from Stripe subscription.
     * Publishes billing.payment.failed event.
     */
    @Transactional
    public void handleInvoicePaymentFailed(Invoice invoice) {
        String subscriptionIdStr = invoice.getMetadata() != null
                ? invoice.getMetadata().get("subscriptionId")
                : null;
        if (subscriptionIdStr == null || subscriptionIdStr.isBlank()) {
            log.warn("invoice.payment_failed: no subscriptionId in metadata, invoiceId={}", invoice.getId());
            return;
        }

        UUID subscriptionId;
        try {
            subscriptionId = UUID.fromString(subscriptionIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("invoice.payment_failed: invalid subscriptionId in metadata: {}", subscriptionIdStr);
            return;
        }

        BillingSubscription subscription = billingSubscriptionService.findBySubscriptionId(subscriptionId)
                .orElse(null);

        String reason = invoice.getAttemptCount() != null && invoice.getAttemptCount() > 1
                ? "payment failed after " + invoice.getAttemptCount() + " attempts"
                : "payment failed";

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", subscriptionId.toString());
        if (subscription != null) {
            payload.put("customerId", subscription.getCustomer().getId().toString());
        }
        payload.put("invoiceId", invoice.getId());
        payload.put("amount", invoice.getAmountDue());
        payload.put("currency", invoice.getCurrency());
        payload.put("attempts", invoice.getAttemptCount());
        payload.put("reason", reason);
        outboxEventService.createEvent("billing.payment.failed", "Payment",
                subscriptionId, payload);

        log.info("invoice.payment_failed processed: subscriptionId={} invoiceId={}", subscriptionId, invoice.getId());
    }

    /**
     * Handle customer.subscription.deleted from Stripe.
     * Marks subscription as canceled and publishes billing.subscription.canceled.
     */
    @Transactional
    public void handleSubscriptionDeleted(Subscription stripeSub) {
        String subscriptionIdStr = stripeSub.getMetadata() != null
                ? stripeSub.getMetadata().get("subscriptionId")
                : null;
        if (subscriptionIdStr == null || subscriptionIdStr.isBlank()) {
            log.warn("customer.subscription.deleted: no subscriptionId in metadata, stripeSubId={}", stripeSub.getId());
            return;
        }

        UUID subscriptionId;
        try {
            subscriptionId = UUID.fromString(subscriptionIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("customer.subscription.deleted: invalid subscriptionId in metadata: {}", subscriptionIdStr);
            return;
        }

        BillingSubscription subscription = billingSubscriptionService.findBySubscriptionId(subscriptionId)
                .orElse(null);
        if (subscription == null) {
            log.warn("customer.subscription.deleted: subscription not found for id={}", subscriptionId);
            return;
        }

        billingSubscriptionService.markCanceled(subscription, Instant.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", subscriptionId.toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("providerSubscriptionRef", stripeSub.getId());
        payload.put("canceledAt", Instant.now().toString());
        outboxEventService.createEvent("billing.subscription.canceled", "Subscription",
                subscriptionId, payload);

        log.info("customer.subscription.deleted processed: subscriptionId={} stripeSubId={}", subscriptionId, stripeSub.getId());
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
