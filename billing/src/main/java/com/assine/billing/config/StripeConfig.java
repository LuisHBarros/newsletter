package com.assine.billing.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Stripe SDK with the API key from {@link StripeProperties} when
 * {@code billing.stripe.enabled=true}. Kept as a separate class so the SDK isn't
 * eagerly configured in local/test profiles where the fake adapter is active.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties properties;

    @PostConstruct
    void initStripe() {
        if (!properties.enabled()) {
            log.info("Stripe integration disabled (billing.stripe.enabled=false); fake adapter will be used.");
            return;
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "billing.stripe.enabled=true but billing.stripe.apiKey is empty");
        }
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new IllegalStateException(
                    "billing.stripe.enabled=true but billing.stripe.webhookSecret is empty");
        }
        Stripe.apiKey = properties.apiKey();
        log.info("Stripe SDK initialized (apiKey length={}, webhookSecret length={})",
                properties.apiKey().length(), properties.webhookSecret().length());
    }
}
