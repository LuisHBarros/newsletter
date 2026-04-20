package com.assine.billing.application.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port for the payment provider (Stripe, etc). Kept intentionally minimal
 * so the domain can drive the flow without coupling to the SDK.
 */
public interface PaymentProviderPort {

    /**
     * Attempt to charge the customer synchronously.
     *
     * @return the provider-side reference (e.g. {@code pi_...}) when successful.
     * @throws RuntimeException when the provider rejects the charge.
     * @deprecated Use {@link #createSubscription} for subscription billing instead.
     */
    @Deprecated
    String charge(UUID customerId, BigDecimal amount, String currency, String idempotencyKey);

    /**
     * Create a subscription on the provider (Stripe) with automatic charging.
     * Uses DEFAULT_INCOMPLETE payment behavior so the front-end can confirm the payment.
     *
     * @param billingCustomerId local customer id
     * @param stripePriceId Stripe Price ID (e.g., price_xxx)
     * @param subscriptionId local subscription id (for metadata)
     * @param idempotencyKey idempotency key for the request
     * @return the provider-side subscription reference (e.g., {@code sub_...})
     * @throws RuntimeException when the provider rejects the request
     */
    String createSubscription(UUID billingCustomerId, String stripePriceId, UUID subscriptionId, String idempotencyKey);

    /**
     * Cancel an in-flight / pending payment on the provider.
     */
    void cancel(String providerPaymentRef);

    /**
     * Cancel the subscription on the provider side.
     * Should be idempotent — canceling an already-canceled subscription must not throw.
     */
    void cancelSubscription(String providerSubscriptionRef);
}
