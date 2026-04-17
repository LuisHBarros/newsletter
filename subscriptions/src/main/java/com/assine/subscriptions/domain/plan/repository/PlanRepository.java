package com.assine.subscriptions.domain.plan.repository;

import com.assine.subscriptions.domain.plan.model.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {
    Plan save(Plan plan);
    Optional<Plan> findById(UUID id);
    List<Plan> findAll();
    Page<Plan> findAll(Pageable pageable);
    List<Plan> findByActive(Boolean active);
    Page<Plan> findByActive(Boolean active, Pageable pageable);
    void delete(UUID id);
    boolean existsById(UUID id);
}
