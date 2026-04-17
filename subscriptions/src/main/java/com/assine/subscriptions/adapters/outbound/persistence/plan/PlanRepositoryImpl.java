package com.assine.subscriptions.adapters.outbound.persistence.plan;

import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlanRepositoryImpl implements PlanRepository {

    private final PlanJpaRepository jpaRepository;

    @Override
    public Plan save(Plan plan) {
        return jpaRepository.save(plan);
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Plan> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Page<Plan> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    @Override
    public List<Plan> findByActive(Boolean active) {
        return jpaRepository.findByActive(active);
    }

    @Override
    public Page<Plan> findByActive(Boolean active, Pageable pageable) {
        return jpaRepository.findByActive(active, pageable);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }
}
