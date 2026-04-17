package com.assine.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code billing.stripe.*} properties. When {@code enabled=true} the
 * {@link com.assine.billing.adapters.outbound.payment.stripe.StripePaymentProviderAdapter}
 * is activated and {@code apiKey} / {@code webhookSecret} are required.
 * When {@code enabled=false} (default) the fake adapter is used and the webhook
 * controller rejects everything with {@code 503}.
 */
@ConfigurationProperties(prefix = "billing.stripe")
public record StripeProperties(
        boolean enabled,
        String apiKey,
        String webhookSecret
) {
    public StripeProperties {
        // Defaults for nullable fields so Spring binding doesn't blow up when keys are absent.
        apiKey = apiKey == null ? "" : apiKey;
        webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }
}
