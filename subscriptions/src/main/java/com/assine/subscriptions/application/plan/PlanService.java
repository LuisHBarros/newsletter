package com.assine.subscriptions.application.plan;

import com.assine.subscriptions.application.outbox.OutboxEventService;
import org.springframework.transaction.annotation.Transactional;
import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.plan.repository.PlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlanService {

    private final PlanRepository planRepository;
    private final OutboxEventService outboxEventService;

    public PlanService(PlanRepository planRepository, OutboxEventService outboxEventService) {
        this.planRepository = planRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Plan createPlan(String name, String description, BigDecimal price, String currency,
                          BillingInterval billingInterval, Integer trialDays, Map<String, Object> features) {
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(description)
                .price(price)
                .currency(currency)
                .billingInterval(billingInterval)
                .trialDays(trialDays != null ? trialDays : 0)
                .features(features != null ? features : Map.of())
                .active(true)
                .build();

        Plan savedPlan = planRepository.save(plan);

        // Publish event
        outboxEventService.createEvent(
                "plan.created",
                "Plan",
                savedPlan.getId(),
                Map.of(
                        "planId", savedPlan.getId().toString(),
                        "name", savedPlan.getName(),
                        "price", savedPlan.getPrice(),
                        "currency", savedPlan.getCurrency(),
                        "billingInterval", savedPlan.getBillingInterval().toString(),
                        "trialDays", savedPlan.getTrialDays(),
                        "active", savedPlan.getActive()
                )
        );

        return savedPlan;
    }

    @Transactional
    public Plan updatePlan(UUID id, String name, String description, BigDecimal price, String currency,
                          BillingInterval billingInterval, Integer trialDays, Map<String, Object> features, Boolean active) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + id));

        if (name != null) plan.setName(name);
        if (description != null) plan.setDescription(description);
        if (price != null) plan.setPrice(price);
        if (currency != null) plan.setCurrency(currency);
        if (billingInterval != null) plan.setBillingInterval(billingInterval);
        if (trialDays != null) plan.setTrialDays(trialDays);
        if (features != null) plan.setFeatures(features);
        if (active != null) plan.setActive(active);

        Plan savedPlan = planRepository.save(plan);

        // Publish event
        outboxEventService.createEvent(
                "plan.updated",
                "Plan",
                savedPlan.getId(),
                Map.of(
                        "planId", savedPlan.getId().toString(),
                        "name", savedPlan.getName(),
                        "price", savedPlan.getPrice(),
                        "currency", savedPlan.getCurrency(),
                        "billingInterval", savedPlan.getBillingInterval().toString(),
                        "trialDays", savedPlan.getTrialDays(),
                        "active", savedPlan.getActive()
                )
        );

        return savedPlan;
    }

    public Plan getPlan(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + id));
    }

    public List<Plan> getAllPlans() {
        return planRepository.findAll();
    }

    public Page<Plan> getAllPlans(Pageable pageable) {
        return planRepository.findAll(pageable);
    }

    public List<Plan> getActivePlans() {
        return planRepository.findByActive(true);
    }

    public Page<Plan> getActivePlans(Pageable pageable) {
        return planRepository.findByActive(true, pageable);
    }

    @Transactional
    public void deletePlan(UUID id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + id));

        planRepository.delete(id);

        // Publish event
        outboxEventService.createEvent(
                "plan.deleted",
                "Plan",
                plan.getId(),
                Map.of(
                        "planId", plan.getId().toString()
                )
        );
    }
}
