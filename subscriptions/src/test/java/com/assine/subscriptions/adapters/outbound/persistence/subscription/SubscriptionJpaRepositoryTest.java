package com.assine.subscriptions.adapters.outbound.persistence.subscription;

import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SubscriptionJpaRepositoryTest {

    @Autowired
    private SubscriptionJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID testId;
    private UUID userId;
    private UUID planId;
    private Plan plan;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        testId = UUID.randomUUID();
        userId = UUID.randomUUID();
        planId = UUID.randomUUID();

        plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium subscription plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .build();

        entityManager.persist(plan);
    }

    @Test
    void shouldSaveAndFindSubscription() {
        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(2592000))
                .build();

        repository.save(subscription);

        assertThat(repository.findById(testId)).isPresent();
        assertThat(repository.findById(testId).get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void shouldFindByUserIdAndPlanId() {
        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        repository.save(subscription);

        Optional<Subscription> found = repository.findByUserIdAndPlanId(userId, planId);
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
    }

    @Test
    void shouldFindByUserId() {
        Subscription sub1 = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        UUID userId2 = UUID.randomUUID();
        Subscription sub2 = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId2)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        repository.save(sub1);
        repository.save(sub2);

        List<Subscription> userSubscriptions = repository.findByUserId(userId);
        assertThat(userSubscriptions).hasSize(1);
        assertThat(userSubscriptions.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void shouldFindByPlanId() {
        Subscription sub1 = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        Subscription sub2 = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.TRIAL)
                .build();

        repository.save(sub1);
        repository.save(sub2);

        List<Subscription> planSubscriptions = repository.findByPlanId(planId);
        assertThat(planSubscriptions).hasSize(2);
    }

    @Test
    void shouldFindByStatus() {
        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        Subscription trialSub = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.TRIAL)
                .build();

        Subscription canceledSub = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.CANCELED)
                .build();

        repository.save(activeSub);
        repository.save(trialSub);
        repository.save(canceledSub);

        List<Subscription> activeSubscriptions = repository.findByStatus(SubscriptionStatus.ACTIVE);
        List<Subscription> trialSubscriptions = repository.findByStatus(SubscriptionStatus.TRIAL);
        List<Subscription> canceledSubscriptions = repository.findByStatus(SubscriptionStatus.CANCELED);

        assertThat(activeSubscriptions).hasSize(1);
        assertThat(trialSubscriptions).hasSize(1);
        assertThat(canceledSubscriptions).hasSize(1);
    }

    @Test
    void shouldCheckExistsByUserIdAndPlanId() {
        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        repository.save(subscription);

        assertThat(repository.existsByUserIdAndPlanId(userId, planId)).isTrue();
        assertThat(repository.existsByUserIdAndPlanId(UUID.randomUUID(), planId)).isFalse();
    }

    @Test
    void shouldEnforceUniqueConstraint() {
        Subscription sub1 = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        repository.save(sub1);

        Subscription sub2 = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.TRIAL)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(sub2)
        );
    }
}
