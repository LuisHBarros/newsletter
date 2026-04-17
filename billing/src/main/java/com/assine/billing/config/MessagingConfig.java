package com.assine.billing.config;

import com.assine.billing.adapters.outbound.messaging.sqs.SqsEventPublisher;
import com.assine.billing.domain.outbox.port.EventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Bean
    public EventPublisher eventPublisher(SqsEventPublisher sqsEventPublisher) {
        return sqsEventPublisher;
    }
}
