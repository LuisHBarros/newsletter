package com.assine.content.adapters.inbound.messaging.sqs;

import com.assine.content.application.issue.IssueImportService;
import com.assine.content.application.webhook.ImportIssueJob;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link ImportIssueJob}s from {@code assine-content-jobs} and delegates
 * to {@link IssueImportService}. Exceptions propagate so SQS re-delivers (max
 * receive count then DLQ).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ContentJobsListener {

    private final IssueImportService importService;

    @SqsListener("${aws.sqs.contentJobs.queue:assine-content-jobs}")
    public void onMessage(@Payload ImportIssueJob job) {
        log.info("Processing ImportIssueJob pageId={} source={}", job.pageId(), job.source());
        IssueImportService.ImportResult r = importService.importByPageId(job.pageId());
        log.info("Imported Notion page {} → issue {} (created={}, justPublished={})",
                job.pageId(), r.issue().getId(), r.created(), r.justPublished());
    }
}
