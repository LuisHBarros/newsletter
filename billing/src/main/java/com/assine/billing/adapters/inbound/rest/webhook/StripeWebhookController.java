package com.assine.billing.adapters.inbound.rest.webhook;

import com.assine.billing.application.payment.StripeWebhookService;
import com.assine.billing.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeProperties properties;
    private final StripeWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (!properties.enabled()) {
            log.warn("Stripe webhook received but integration is disabled");
            return ResponseEntity.status(503).body(Map.of("error", "stripe disabled"));
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing Stripe-Signature header"));
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, properties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid signature"));
        }

        try {
            webhookService.processWithDedup(event);
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate Stripe webhook skipped: id={} type={}", event.getId(), event.getType());
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
