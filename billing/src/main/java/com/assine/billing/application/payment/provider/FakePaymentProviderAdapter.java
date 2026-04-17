package com.assine.billing.application.payment.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default {@link PaymentProviderPort} used when {@code billing.stripe.enabled} is
 * absent or {@code false}. Synchronously returns a successful provider reference
 * so the subscription.requested → billing.payment.succeeded flow can be
 * exercised in dev/local/test without external calls.
 *
 * <p>When {@code billing.stripe.enabled=true} the real
 * {@link com.assine.billing.adapters.outbound.payment.stripe.StripePaymentProviderAdapter}
 * is activated instead (and this bean is skipped).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "billing.stripe", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakePaymentProviderAdapter implements PaymentProviderPort {

    @Override
    public String charge(UUID customerId, BigDecimal amount, String currency, String idempotencyKey) {
        String ref = "fake_pi_" + UUID.randomUUID();
        log.info("FAKE charge: customerId={} amount={} {} idempotencyKey={} -> ref={}",
            customerId, amount, currency, idempotencyKey, ref);
        return ref;
    }

    @Override
    public void cancel(String providerPaymentRef) {
        log.info("FAKE cancel: providerPaymentRef={}", providerPaymentRef);
    }

    @Override
    public void cancelSubscription(String providerSubscriptionRef) {
        log.info("FAKE cancelSubscription: providerSubscriptionRef={}", providerSubscriptionRef);
    }
}
