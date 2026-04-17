package com.assine.billing.adapters.outbound.persistence.plan;

import com.assine.billing.domain.plan.model.BillingPlan;
import com.assine.billing.domain.plan.repository.BillingPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BillingPlanRepositoryImpl implements BillingPlanRepository {

    private final BillingPlanJpaRepository jpaRepository;

    @Override
    public BillingPlan save(BillingPlan plan) {
        return jpaRepository.save(plan);
    }

    @Override
    public Optional<BillingPlan> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<BillingPlan> findByPlanId(UUID planId) {
        return jpaRepository.findByPlanId(planId);
    }

    @Override
    public List<BillingPlan> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void deleteByPlanId(UUID planId) {
        jpaRepository.deleteByPlanId(planId);
    }
}
