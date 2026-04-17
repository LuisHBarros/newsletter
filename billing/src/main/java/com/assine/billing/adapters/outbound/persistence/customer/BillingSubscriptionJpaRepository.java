package com.assine.billing.adapters.outbound.persistence.customer;

import com.assine.billing.domain.customer.model.BillingSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingSubscriptionJpaRepository extends JpaRepository<BillingSubscription, UUID> {
    Optional<BillingSubscription> findBySubscriptionId(UUID subscriptionId);
}
