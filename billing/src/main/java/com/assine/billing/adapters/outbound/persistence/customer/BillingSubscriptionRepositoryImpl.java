package com.assine.billing.adapters.outbound.persistence.customer;

import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.repository.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BillingSubscriptionRepositoryImpl implements BillingSubscriptionRepository {

    private final BillingSubscriptionJpaRepository jpaRepository;

    @Override
    public BillingSubscription save(BillingSubscription subscription) {
        return jpaRepository.save(subscription);
    }

    @Override
    public Optional<BillingSubscription> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<BillingSubscription> findBySubscriptionId(UUID subscriptionId) {
        return jpaRepository.findBySubscriptionId(subscriptionId);
    }
}
