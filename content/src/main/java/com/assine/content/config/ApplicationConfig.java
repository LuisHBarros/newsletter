package com.assine.content.config;

import com.assine.content.application.newsletter.NewsletterService;
import com.assine.content.application.outbox.OutboxEventService;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({NotionProperties.class, ContentProperties.class, SqsProperties.class})
public class ApplicationConfig {

    @Bean
    public OutboxEventService outboxEventService(OutboxEventRepository repo) {
        return new OutboxEventService(repo);
    }

    @Bean
    public NewsletterService newsletterService(NewsletterRepository repo, OutboxEventService outbox) {
        return new NewsletterService(repo, outbox);
    }
}
