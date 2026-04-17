package com.assine.subscriptions.adapters.outbound.persistence.subscription;

import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import com.assine.subscriptions.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionRepositoryImpl implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpaRepository;

    @Override
    public Subscription save(Subscription subscription) {
        return jpaRepository.save(subscription);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Subscription> findByUserIdAndPlanId(UUID userId, UUID planId) {
        return jpaRepository.findByUserIdAndPlanId(userId, planId);
    }

    @Override
    public List<Subscription> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public Page<Subscription> findByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public List<Subscription> findByPlanId(UUID planId) {
        return jpaRepository.findByPlanId(planId);
    }

    @Override
    public Page<Subscription> findByPlanId(UUID planId, Pageable pageable) {
        return jpaRepository.findByPlanId(planId, pageable);
    }

    @Override
    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable) {
        return jpaRepository.findByStatus(status, pageable);
    }

    @Override
    public Page<Subscription> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByUserIdAndPlanId(UUID userId, UUID planId) {
        return jpaRepository.existsByUserIdAndPlanId(userId, planId);
    }

    @Override
    public List<Subscription> findExpirable(Instant now) {
        return jpaRepository.findExpirable(now);
    }
}
