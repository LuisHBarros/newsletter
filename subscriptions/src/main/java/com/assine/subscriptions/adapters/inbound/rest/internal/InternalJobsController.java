package com.assine.subscriptions.adapters.inbound.rest.internal;

import com.assine.subscriptions.application.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Internal endpoints for job triggers (e.g., EventBridge Scheduler).
 * Protected by admin scope - not for public use.
 */
@Slf4j
@RestController
@RequestMapping("/internal/jobs")
@RequiredArgsConstructor
public class InternalJobsController {

    private final SubscriptionService subscriptionService;

    /**
     * Triggers expiration of subscriptions whose current period has ended.
     * Intended to be called by EventBridge Scheduler in production.
     * In dev, the @Scheduled ExpirationScheduler runs this automatically.
     */
    @PostMapping("/expire-subscriptions")
    @PreAuthorize("hasAuthority('SCOPE_subscriptions:admin')")
    public ResponseEntity<Map<String, Object>> expireSubscriptions() {
        log.info("Internal job triggered: expire-subscriptions");
        int expired = subscriptionService.expireDueSubscriptions();
        return ResponseEntity.ok(Map.of(
                "expired", expired,
                "timestamp", Instant.now().toString()
        ));
    }
}
