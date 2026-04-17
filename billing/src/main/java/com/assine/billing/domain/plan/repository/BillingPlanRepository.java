package com.assine.billing.domain.plan.repository;

import com.assine.billing.domain.plan.model.BillingPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPlanRepository {
    BillingPlan save(BillingPlan plan);
    Optional<BillingPlan> findById(UUID id);
    Optional<BillingPlan> findByPlanId(UUID planId);
    List<BillingPlan> findAll();
    void deleteByPlanId(UUID planId);
}
