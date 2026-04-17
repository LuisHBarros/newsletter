package com.assine.billing.domain.customer.repository;

import com.assine.billing.domain.customer.model.BillingSubscription;

import java.util.Optional;
import java.util.UUID;

public interface BillingSubscriptionRepository {
    BillingSubscription save(BillingSubscription subscription);
    Optional<BillingSubscription> findById(UUID id);
    Optional<BillingSubscription> findBySubscriptionId(UUID subscriptionId);
}
