package com.assine.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "aws.sqs")
public class SqsProperties {

    private QueueConfig events = new QueueConfig();
    private QueueConfig contentJobs = new QueueConfig();
    private ListenerConfig listener = new ListenerConfig();

    @Data
    public static class QueueConfig {
        private String queue;
        private String dlq;
    }

    @Data
    public static class ListenerConfig {
        private int maxMessagesPerPoll = 10;
        private Duration pollTimeout = Duration.ofSeconds(10);
        private Duration messageVisibility = Duration.ofSeconds(30);
        private int maxReceiveCount = 5;
    }
}
