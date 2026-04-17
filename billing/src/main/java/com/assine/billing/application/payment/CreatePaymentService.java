package com.assine.billing.application.payment;

import com.assine.billing.application.outbox.OutboxEventService;
import com.assine.billing.application.payment.provider.PaymentProviderPort;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.payment.exception.PaymentException;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ported from {@code CreateTransferService}. Runs authorization, creates a {@link Payment}
 * row in {@link PaymentStatus#PENDING} and asks the provider to register the charge
 * via {@link PaymentProviderPort}.
 *
 * <p>In the async provider flow (real Stripe), the provider returns immediately with
 * the {@code pi_...} reference and success/failure is delivered later via the webhook
 * — so this service only publishes {@code billing.payment.failed} when the provider
 * itself rejects the creation synchronously; {@code billing.payment.succeeded} and
 * {@code billing.subscription.activated} are emitted by
 * {@link StripeWebhookService} when the corresponding webhook arrives.
 *
 * <p>With the fake adapter, {@code charge} always succeeds synchronously: the row
 * stays {@link PaymentStatus#PENDING} and the fake webhook flow driven by tests
 * (or a stub) is responsible for completing the lifecycle, mirroring production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuthorizationService authorizationService;
    private final PaymentProviderPort paymentProvider;
    private final OutboxEventService outboxEventService;

    /**
     * @param subscription optional — when present, subscription-lifecycle events will be emitted.
     * @param idempotencyKey used as provider idempotency-key; typically {@code eventId} of the inbound event.
     * @return payment id
     */
    @Transactional
    public UUID execute(UUID customerId,
                        BillingSubscription subscription,
                        BigDecimal amount,
                        String currency,
                        String idempotencyKey) {
        BillingCustomer customer = authorizationService.authorize(customerId, amount, currency);

        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .customer(customer)
            .subscription(subscription)
            .amount(amount)
            .currency(currency.toUpperCase())
            .status(PaymentStatus.PENDING)
            .provider("FAKE")
            .build();
        paymentRepository.save(payment);

        try {
            String providerRef = paymentProvider.charge(customer.getId(), amount, currency, idempotencyKey);
            payment.setProviderPaymentRef(providerRef);
            // Async flow: status stays PENDING until the webhook confirms success/failure.
            paymentRepository.save(payment);
            log.info("Payment registered with provider: id={} providerRef={} subscriptionId={}",
                payment.getId(), providerRef,
                subscription != null ? subscription.getSubscriptionId() : null);
        } catch (RuntimeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);

            publishFailed(payment, subscription, e.getMessage());
            log.error("Payment failed synchronously at provider: id={} reason={}", payment.getId(), e.getMessage());

            throw new PaymentException("payment failed: " + e.getMessage(), e);
        }

        return payment.getId();
    }

    private void publishFailed(Payment payment, BillingSubscription subscription, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId().toString());
        if (subscription != null) {
            payload.put("subscriptionId", subscription.getSubscriptionId().toString());
        }
        payload.put("attempts", 1);
        payload.put("reason", reason);
        outboxEventService.createEvent("billing.payment.failed", "Payment", payment.getId(), payload);
    }
}
