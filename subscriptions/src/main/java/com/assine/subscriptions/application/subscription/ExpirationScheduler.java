package com.assine.subscriptions.application.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job to expire subscriptions whose current period has ended
 * without renewal.
 *
 * Enabled by default in dev (via @Scheduled), disabled in prod where
 * EventBridge Scheduler should trigger the job via InternalJobsController.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "subscriptions.expiration.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ExpirationScheduler {

    private final SubscriptionService subscriptionService;

    /**
     * Runs every hour to expire overdue subscriptions.
     * In production, this cron should be disabled and triggered via
     * EventBridge Scheduler calling the internal endpoint.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    @SchedulerLock(name = "expire-subscriptions", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void expireDueSubscriptions() {
        log.info("Running expiration job...");
        int expired = subscriptionService.expireDueSubscriptions();
        log.info("Expired {} subscriptions", expired);
    }
}
