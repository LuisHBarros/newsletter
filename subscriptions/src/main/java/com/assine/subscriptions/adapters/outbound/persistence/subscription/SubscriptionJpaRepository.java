package com.assine.subscriptions.adapters.outbound.persistence.subscription;

import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionJpaRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByUserIdAndPlanId(UUID userId, UUID planId);
    List<Subscription> findByUserId(UUID userId);
    Page<Subscription> findByUserId(UUID userId, Pageable pageable);
    List<Subscription> findByPlanId(UUID planId);
    Page<Subscription> findByPlanId(UUID planId, Pageable pageable);
    List<Subscription> findByStatus(SubscriptionStatus status);
    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);
    boolean existsByUserIdAndPlanId(UUID userId, UUID planId);

    @Query("SELECT s FROM Subscription s WHERE s.status IN ('ACTIVE', 'PAST_DUE') " +
           "AND s.currentPeriodEnd < :now AND s.cancelAtPeriodEnd = false")
    List<Subscription> findExpirable(@Param("now") Instant now);
}
