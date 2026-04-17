package com.assine.billing.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class SqsListenerConfig {

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            SqsProperties sqsProperties) {

        SqsProperties.ListenerConfig listener = sqsProperties.getListener();

        log.info("Configuring SQS listener container: maxMessagesPerPoll={}, pollTimeout={}, " +
                 "messageVisibility={}, acknowledgementMode=ON_SUCCESS",
                listener.getMaxMessagesPerPoll(),
                listener.getPollTimeout(),
                listener.getMessageVisibility());

        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .maxMessagesPerPoll(listener.getMaxMessagesPerPoll())
                        .pollTimeout(listener.getPollTimeout())
                        .messageVisibility(listener.getMessageVisibility())
                        .acknowledgementMode(AcknowledgementMode.ON_SUCCESS))
                .build();
    }
}
