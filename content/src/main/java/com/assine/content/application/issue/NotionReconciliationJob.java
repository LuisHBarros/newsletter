package com.assine.content.application.issue;

import com.assine.content.adapters.outbound.messaging.sqs.SqsContentJobsPublisher;
import com.assine.content.application.webhook.ImportIssueJob;
import com.assine.content.domain.issue.repository.IssueRepository;
import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.model.NewsletterStatus;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import com.assine.content.domain.notion.port.NotionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fallback in case a Notion webhook is missed: queries each newsletter's database
 * for pages edited after the newsletter's most recent imported {@code notionLastEditedAt}
 * and enqueues {@link ImportIssueJob}s on the content-jobs queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionReconciliationJob {

    private static final int PER_DATABASE_LIMIT = 50;
    /** Cold-start lookback when a newsletter has no imported issue yet. */
    private static final Duration COLD_START_LOOKBACK = Duration.ofDays(30);

    private final NewsletterRepository newsletterRepository;
    private final IssueRepository issueRepository;
    private final NotionPort notionPort;
    private final SqsContentJobsPublisher jobsPublisher;

    public int reconcile() {
        int enqueued = 0;
        for (Newsletter n : newsletterRepository.findAll()) {
            if (n.getStatus() != NewsletterStatus.ACTIVE) continue;
            Instant since = issueRepository.findLatestNotionEditedAt(n.getId())
                    .orElseGet(() -> Instant.now().minus(COLD_START_LOOKBACK));
            try {
                List<NotionPort.NotionPageSummary> pages =
                        notionPort.queryRecentlyEdited(n.getNotionDatabaseId(), since, PER_DATABASE_LIMIT);
                for (NotionPort.NotionPageSummary p : pages) {
                    jobsPublisher.enqueueImport(new ImportIssueJob(p.pageId(), p.lastEditedAt(), "reconcile"));
                    enqueued++;
                }
            } catch (Exception e) {
                log.warn("Reconciliation failed for newsletter {} ({}): {}", n.getSlug(), n.getNotionDatabaseId(), e.getMessage());
            }
        }
        if (enqueued > 0) log.info("Reconciliation enqueued {} import job(s)", enqueued);
        return enqueued;
    }

    @Scheduled(cron = "${content.scheduler.reconcile-notion.cron:0 */15 * * * *}")
    @ConditionalOnProperty(prefix = "content.scheduler.reconcile-notion", name = "enabled", havingValue = "true", matchIfMissing = true)
    @SchedulerLock(name = "content-reconcile-notion", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() { reconcile(); }
}
