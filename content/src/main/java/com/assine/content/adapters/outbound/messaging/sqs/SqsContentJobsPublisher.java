package com.assine.content.adapters.outbound.messaging.sqs;

import com.assine.content.application.webhook.ImportIssueJob;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Enqueues import jobs onto the {@code assine-content-jobs} SQS queue. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsContentJobsPublisher {

    private final SqsTemplate sqsTemplate;

    @Value("${aws.sqs.contentJobs.queue:assine-content-jobs}")
    private String jobsQueue;

    public void enqueueImport(ImportIssueJob job) {
        try {
            sqsTemplate.send(jobsQueue, job);
            log.debug("Enqueued ImportIssueJob pageId={} source={}", job.pageId(), job.source());
        } catch (Exception e) {
            log.error("Failed to enqueue ImportIssueJob pageId={}: {}", job.pageId(), e.getMessage(), e);
            throw new RuntimeException("Failed to enqueue content job", e);
        }
    }
}
