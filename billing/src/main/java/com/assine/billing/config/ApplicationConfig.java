package com.assine.billing.config;

import com.assine.billing.adapters.outbound.persistence.outbox.OutboxEventRepositoryImpl;
import com.assine.billing.application.outbox.OutboxEventService;
import com.assine.billing.domain.outbox.repository.OutboxEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public OutboxEventRepository outboxEventRepository(OutboxEventRepositoryImpl outboxEventRepositoryImpl) {
        return outboxEventRepositoryImpl;
    }

    @Bean
    public OutboxEventService outboxEventService(OutboxEventRepository outboxEventRepository) {
        return new OutboxEventService(outboxEventRepository);
    }
}
