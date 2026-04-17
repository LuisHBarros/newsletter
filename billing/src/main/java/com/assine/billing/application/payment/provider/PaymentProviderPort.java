package com.assine.billing.application.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port for the payment provider (Stripe, etc). Kept intentionally minimal
 * so the domain can drive the flow without coupling to the SDK. A real Stripe
 * implementation lives in a future adapter; for now {@link FakePaymentProviderAdapter}
 * synchronously "charges" successfully so the event flow is exercisable end-to-end.
 */
public interface PaymentProviderPort {

    /**
     * Attempt to charge the customer synchronously.
     *
     * @return the provider-side reference (e.g. {@code pi_...}) when successful.
     * @throws RuntimeException when the provider rejects the charge.
     */
    String charge(UUID customerId, BigDecimal amount, String currency, String idempotencyKey);

    /**
     * Cancel an in-flight / pending payment on the provider.
     */
    void cancel(String providerPaymentRef);
}
