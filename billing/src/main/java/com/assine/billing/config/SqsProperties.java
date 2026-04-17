package com.assine.billing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "aws.sqs")
public class SqsProperties {

    private QueueConfig events = new QueueConfig();
    private QueueConfig inbound = new QueueConfig();
    private ListenerConfig listener = new ListenerConfig();

    @Data
    public static class QueueConfig {
        private String queue;
        private String dlq;
    }

    @Data
    public static class ListenerConfig {
        private int maxMessagesPerPoll = 10;
        private java.time.Duration pollTimeout = java.time.Duration.ofSeconds(10);
        private java.time.Duration messageVisibility = java.time.Duration.ofSeconds(30);
        private int maxReceiveCount = 5;
    }
}
