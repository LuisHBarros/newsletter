package com.assine.billing.adapters.outbound.persistence.plan;

import com.assine.billing.domain.plan.model.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPlanJpaRepository extends JpaRepository<BillingPlan, UUID> {
    Optional<BillingPlan> findByPlanId(UUID planId);
    void deleteByPlanId(UUID planId);
}
