package com.assine.content.application.issue;

import com.assine.content.application.outbox.OutboxEventService;
import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.model.IssueStatus;
import com.assine.content.domain.issue.repository.IssueRepository;
import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transitions {@code SCHEDULED} issues whose {@code scheduledAt} has elapsed to
 * {@code PUBLISHED} and enqueues the {@code content.issue.published} event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueScheduleService {

    private static final int BATCH_SIZE = 50;

    private final IssueRepository issueRepository;
    private final NewsletterRepository newsletterRepository;
    private final OutboxEventService outboxEventService;

    @Transactional
    public int publishDue() {
        List<Issue> due = issueRepository.findDueForPublishing(Instant.now(), BATCH_SIZE);
        for (Issue issue : due) {
            issue.setStatus(IssueStatus.PUBLISHED);
            issue.setPublishedAt(Instant.now());
            issueRepository.save(issue);

            Newsletter newsletter = newsletterRepository.findById(issue.getNewsletterId())
                    .orElseThrow(() -> new IllegalStateException("Newsletter missing for issue " + issue.getId()));
            List<UUID> planIds = newsletterRepository.findPlanIdsByNewsletterId(newsletter.getId());

            outboxEventService.createEvent(
                    "content.issue.published",
                    "Issue",
                    issue.getId(),
                    Map.of(
                            "newsletterId", newsletter.getId().toString(),
                            "newsletterSlug", newsletter.getSlug(),
                            "issueId", issue.getId().toString(),
                            "title", issue.getTitle(),
                            "slug", issue.getSlug(),
                            "htmlS3Key", Objects.requireNonNull(issue.getHtmlS3Key(), "htmlS3Key must not be null for issue " + issue.getId()),
                            "publishedAt", issue.getPublishedAt().toString(),
                            "planIds", planIds.stream().map(UUID::toString).toList()
                    ));
            log.info("Published scheduled issue {} (newsletter={})", issue.getId(), newsletter.getSlug());
        }
        return due.size();
    }
}
