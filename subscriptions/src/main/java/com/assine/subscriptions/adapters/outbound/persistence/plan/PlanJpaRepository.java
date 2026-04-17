package com.assine.subscriptions.adapters.outbound.persistence.plan;

import com.assine.subscriptions.domain.plan.model.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanJpaRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByIdAndActive(UUID id, Boolean active);
    List<Plan> findByActive(Boolean active);
    Page<Plan> findByActive(Boolean active, Pageable pageable);
}
