package com.assine.billing.adapters.outbound.payment.stripe;

import com.assine.billing.application.payment.provider.PaymentProviderPort;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.repository.BillingCustomerRepository;
import com.assine.billing.domain.payment.exception.PaymentException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Real Stripe adapter. Active only when {@code billing.stripe.enabled=true}.
 *
 * <p>Async flow: {@link #charge} creates a {@link PaymentIntent} with
 * {@code confirm=false} and returns its id (e.g. {@code pi_...}). The actual
 * success/failure is delivered asynchronously via webhook
 * ({@code payment_intent.succeeded} / {@code payment_intent.payment_failed})
 * and processed by
 * {@link com.assine.billing.adapters.inbound.rest.webhook.StripeWebhookController}.
 *
 * <p>Client-side confirmation (SetupIntent + PaymentMethod attach) is expected
 * to happen out-of-band before the PaymentIntent is confirmed — that integration
 * is owned by the front-end / checkout flow and not by this service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "billing.stripe", name = "enabled", havingValue = "true")
public class StripePaymentProviderAdapter implements PaymentProviderPort {

    private final BillingCustomerRepository customerRepository;

    @Override
    public String charge(UUID customerId, BigDecimal amount, String currency, String idempotencyKey) {
        BillingCustomer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new PaymentException("customer not found: " + customerId));

        String stripeCustomerId = ensureStripeCustomer(customer, idempotencyKey);
        long amountMinor = amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinor)
                .setCurrency(currency.toLowerCase())
                .setCustomer(stripeCustomerId)
                .setConfirm(false)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .putMetadata("billingCustomerId", customer.getId().toString())
                .putMetadata("idempotencyKey", idempotencyKey)
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            log.info("Stripe PaymentIntent created: id={} customer={} amount={}{} idempotencyKey={}",
                    intent.getId(), stripeCustomerId, amountMinor, currency, idempotencyKey);
            return intent.getId();
        } catch (StripeException e) {
            throw new PaymentException("stripe charge failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(String providerPaymentRef) {
        if (providerPaymentRef == null || providerPaymentRef.isBlank()) {
            return;
        }
        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerPaymentRef);
            intent.cancel(PaymentIntentCancelParams.builder().build());
            log.info("Stripe PaymentIntent canceled: id={}", providerPaymentRef);
        } catch (StripeException e) {
            // Canceling an already-terminal PI is non-fatal; log and swallow so callers
            // (e.g. subscription.cancel_requested handlers) aren't blocked on idempotent retries.
            log.warn("Stripe PaymentIntent cancel failed for {}: {}", providerPaymentRef, e.getMessage());
        }
    }

    /**
     * Find or create the Stripe Customer for this {@link BillingCustomer} and
     * persist the provider reference back on the aggregate.
     */
    String ensureStripeCustomer(BillingCustomer customer, String idempotencyKey) {
        String existing = customer.getProviderCustomerRef();
        if (existing != null && existing.startsWith("cus_")) {
            return existing;
        }
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(customer.getEmail())
                .putMetadata("userId", customer.getUserId().toString())
                .putMetadata("billingCustomerId", customer.getId().toString())
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("customer-" + idempotencyKey)
                .build();
        try {
            Customer created = Customer.create(params, options);
            customer.setProvider("STRIPE");
            customer.setProviderCustomerRef(created.getId());
            customerRepository.save(customer);
            return created.getId();
        } catch (StripeException e) {
            throw new PaymentException("stripe customer create failed: " + e.getMessage(), e);
        }
    }

}
