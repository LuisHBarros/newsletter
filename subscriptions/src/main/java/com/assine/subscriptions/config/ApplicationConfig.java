package com.assine.subscriptions.config;

import com.assine.subscriptions.adapters.outbound.persistence.outbox.OutboxEventRepositoryImpl;
import com.assine.subscriptions.adapters.outbound.persistence.plan.PlanRepositoryImpl;
import com.assine.subscriptions.adapters.outbound.persistence.subscription.SubscriptionRepositoryImpl;
import com.assine.subscriptions.application.outbox.OutboxEventService;
import com.assine.subscriptions.application.plan.PlanService;
import com.assine.subscriptions.application.subscription.SubscriptionService;
import com.assine.subscriptions.domain.outbox.repository.OutboxEventRepository;
import com.assine.subscriptions.domain.plan.repository.PlanRepository;
import com.assine.subscriptions.domain.subscription.repository.SubscriptionRepository;
import com.assine.subscriptions.domain.subscription.service.SubscriptionStateGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public PlanRepository planRepository(PlanRepositoryImpl planRepositoryImpl) {
        return planRepositoryImpl;
    }

    @Bean
    public SubscriptionRepository subscriptionRepository(SubscriptionRepositoryImpl subscriptionRepositoryImpl) {
        return subscriptionRepositoryImpl;
    }

    @Bean
    public OutboxEventRepository outboxEventRepository(OutboxEventRepositoryImpl outboxEventRepositoryImpl) {
        return outboxEventRepositoryImpl;
    }

    @Bean
    public OutboxEventService outboxEventService(OutboxEventRepository outboxEventRepository) {
        return new OutboxEventService(outboxEventRepository);
    }

    @Bean
    public PlanService planService(PlanRepository planRepository, OutboxEventService outboxEventService) {
        return new PlanService(planRepository, outboxEventService);
    }

    @Bean
    public SubscriptionService subscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
                                                  OutboxEventService outboxEventService, SubscriptionStateGuard stateGuard) {
        return new SubscriptionService(subscriptionRepository, planRepository, outboxEventService, stateGuard);
    }
}
