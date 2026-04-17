package com.assine.billing.application.customer;

import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.model.BillingSubscriptionStatus;
import com.assine.billing.domain.customer.repository.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSubscriptionService {

    private final BillingSubscriptionRepository subscriptionRepository;

    @Transactional
    public BillingSubscription findOrCreate(UUID subscriptionId,
                                            BillingCustomer customer,
                                            UUID planId,
                                            String billingInterval) {
        return subscriptionRepository.findBySubscriptionId(subscriptionId)
            .orElseGet(() -> {
                Instant now = Instant.now();
                Instant periodEnd = now.plus(periodDays(billingInterval), ChronoUnit.DAYS);
                BillingSubscription created = BillingSubscription.builder()
                    .id(UUID.randomUUID())
                    .subscriptionId(subscriptionId)
                    .customer(customer)
                    .planId(planId)
                    .status(BillingSubscriptionStatus.PENDING)
                    .providerSubscriptionRef("fake_sub_" + UUID.randomUUID())
                    .currentPeriodStart(now)
                    .currentPeriodEnd(periodEnd)
                    .billingInterval(billingInterval)
                    .build();
                BillingSubscription saved = subscriptionRepository.save(created);
                log.info("Provisioned billing subscription: id={} subscriptionId={}",
                    saved.getId(), subscriptionId);
                return saved;
            });
    }

    @Transactional
    public void markActive(BillingSubscription subscription) {
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(Instant.now().plus(periodDays(subscription.getBillingInterval()), ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void markTrialActive(BillingSubscription subscription, int trialDays) {
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(Instant.now().plus(trialDays, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void markCanceled(BillingSubscription subscription, Instant canceledAt) {
        subscription.setStatus(BillingSubscriptionStatus.CANCELED);
        subscription.setCanceledAt(canceledAt != null ? canceledAt : Instant.now());
        subscriptionRepository.save(subscription);
    }

    private long periodDays(String billingInterval) {
        if (billingInterval == null) return 30L;
        return switch (billingInterval.toUpperCase()) {
            case "YEARLY" -> 365L;
            case "MONTHLY" -> 30L;
            default -> 30L;
        };
    }
}
