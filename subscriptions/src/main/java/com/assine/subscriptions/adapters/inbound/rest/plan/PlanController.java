package com.assine.subscriptions.adapters.inbound.rest.plan;

import com.assine.subscriptions.adapters.inbound.rest.common.PageResponse;
import com.assine.subscriptions.adapters.inbound.rest.plan.dto.CreatePlanRequest;
import com.assine.subscriptions.adapters.inbound.rest.plan.dto.PlanResponse;
import com.assine.subscriptions.adapters.inbound.rest.plan.dto.UpdatePlanRequest;
import com.assine.subscriptions.application.plan.PlanService;
import com.assine.subscriptions.domain.plan.model.Plan;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_subscriptions:admin')")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        Plan plan = planService.createPlan(
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.billingInterval(),
                request.trialDays(),
                request.features()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(plan));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable UUID id) {
        Plan plan = planService.getPlan(id);
        return ResponseEntity.ok(toResponse(plan));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PlanResponse>> getAllPlans(
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        Page<Plan> plans;
        if (active != null && active) {
            plans = planService.getActivePlans(pageable);
        } else {
            plans = planService.getAllPlans(pageable);
        }

        Page<PlanResponse> mapped = plans.map(this::toResponse);
        return ResponseEntity.ok(PageResponse.from(mapped));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_subscriptions:admin')")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest request) {
        Plan plan = planService.updatePlan(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.billingInterval(),
                request.trialDays(),
                request.features(),
                request.active()
        );
        return ResponseEntity.ok(toResponse(plan));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_subscriptions:admin')")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    private PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                plan.getTrialDays(),
                plan.getFeatures(),
                plan.getActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
