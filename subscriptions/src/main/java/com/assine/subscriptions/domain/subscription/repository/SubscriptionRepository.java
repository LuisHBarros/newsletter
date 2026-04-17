package com.assine.subscriptions.domain.subscription.repository;

import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    Subscription save(Subscription subscription);
    Optional<Subscription> findById(UUID id);
    Optional<Subscription> findByUserIdAndPlanId(UUID userId, UUID planId);
    List<Subscription> findByUserId(UUID userId);
    Page<Subscription> findByUserId(UUID userId, Pageable pageable);
    List<Subscription> findByPlanId(UUID planId);
    Page<Subscription> findByPlanId(UUID planId, Pageable pageable);
    List<Subscription> findByStatus(SubscriptionStatus status);
    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);
    Page<Subscription> findAll(Pageable pageable);
    void delete(UUID id);
    boolean existsById(UUID id);
    boolean existsByUserIdAndPlanId(UUID userId, UUID planId);

    /**
     * Finds subscriptions that should be expired: ACTIVE or PAST_DUE with
     * currentPeriodEnd in the past and not marked for cancellation at period end.
     */
    List<Subscription> findExpirable(Instant now);
}
