package com.assine.subscriptions.config;

import com.assine.subscriptions.adapters.outbound.messaging.sqs.SqsEventPublisher;
import com.assine.subscriptions.domain.outbox.port.EventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Bean
    public EventPublisher eventPublisher(SqsEventPublisher sqsEventPublisher) {
        return sqsEventPublisher;
    }
}
