package com.assine.subscriptions.adapters.outbound.persistence.plan;

import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PlanJpaRepositoryTest {

    @Autowired
    private PlanJpaRepository repository;

    private UUID testId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        testId = UUID.randomUUID();
    }

    @Test
    void shouldSaveAndFindPlan() {
        Plan plan = Plan.builder()
                .id(testId)
                .name("Premium Plan")
                .description("Premium subscription plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1", "feature2", "Feature 2"))
                .active(true)
                .build();

        repository.save(plan);

        assertThat(repository.findById(testId)).isPresent();
        assertThat(repository.findById(testId).get().getName()).isEqualTo("Premium Plan");
    }

    @Test
    void shouldFindByIdAndActive() {
        Plan activePlan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Active Plan")
                .description("Active plan")
                .price(new BigDecimal("19.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(7)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .build();

        Plan inactivePlan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Inactive Plan")
                .description("Inactive plan")
                .price(new BigDecimal("9.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of("feature1", "Feature 1"))
                .active(false)
                .build();

        repository.save(activePlan);
        repository.save(inactivePlan);

        Optional<Plan> foundActive = repository.findByIdAndActive(activePlan.getId(), true);
        Optional<Plan> foundInactive = repository.findByIdAndActive(inactivePlan.getId(), false);

        assertThat(foundActive).isPresent();
        assertThat(foundInactive).isPresent();
        assertThat(repository.findByIdAndActive(inactivePlan.getId(), true)).isEmpty();
    }

    @Test
    void shouldFindByActive() {
        Plan activePlan1 = Plan.builder()
                .id(UUID.randomUUID())
                .name("Active Plan 1")
                .description("Active plan 1")
                .price(new BigDecimal("19.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(7)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .build();

        Plan activePlan2 = Plan.builder()
                .id(UUID.randomUUID())
                .name("Active Plan 2")
                .description("Active plan 2")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1", "feature2", "Feature 2"))
                .active(true)
                .build();

        Plan inactivePlan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Inactive Plan")
                .description("Inactive plan")
                .price(new BigDecimal("9.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of("feature1", "Feature 1"))
                .active(false)
                .build();

        repository.save(activePlan1);
        repository.save(activePlan2);
        repository.save(inactivePlan);

        List<Plan> activePlans = repository.findByActive(true);
        List<Plan> inactivePlans = repository.findByActive(false);

        assertThat(activePlans).hasSize(2);
        assertThat(inactivePlans).hasSize(1);
    }

    @Test
    void shouldDeletePlan() {
        Plan plan = Plan.builder()
                .id(testId)
                .name("Test Plan")
                .description("Test plan")
                .price(new BigDecimal("19.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(7)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .build();

        repository.save(plan);
        repository.deleteById(testId);

        assertThat(repository.findById(testId)).isEmpty();
    }
}
