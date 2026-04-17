package com.assine.content.application.issue;

import com.assine.content.adapters.outbound.render.HtmlRenderer;
import com.assine.content.adapters.outbound.storage.s3.S3ContentUploader;
import com.assine.content.application.outbox.OutboxEventService;
import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.model.IssueStatus;
import com.assine.content.domain.issue.repository.IssueRepository;
import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import com.assine.content.domain.notion.model.NotionPage;
import com.assine.content.domain.notion.port.NotionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Orchestrates the import of a Notion page into an {@code Issue}:
 *  1. Fetch page via {@link NotionPort} (idempotent by {@code notionLastEditedAt}).
 *  2. Render HTML via {@link HtmlRenderer} and upload to S3.
 *  3. Transition state machine and, if published, enqueue {@code content.issue.published}
 *     via the outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueImportService {

    private final NotionPort notionPort;
    private final IssueRepository issueRepository;
    private final NewsletterRepository newsletterRepository;
    private final HtmlRenderer htmlRenderer;
    private final S3ContentUploader s3Uploader;
    private final OutboxEventService outboxEventService;
    private final SlugService slugService;

    @Transactional
    public ImportResult importByPageId(String notionPageId) {
        NotionPage page = notionPort.fetchPage(notionPageId)
                .orElseThrow(() -> new IllegalArgumentException("Notion page not found: " + notionPageId));

        String databaseId = page.getDatabaseId();
        Newsletter newsletter = newsletterRepository.findByNotionDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalStateException(
                        "No newsletter mapped to Notion database " + databaseId));

        Optional<Issue> existing = issueRepository.findByNotionPageId(notionPageId);

        if (existing.isPresent()
                && page.getLastEditedAt() != null
                && existing.get().getNotionLastEditedAt() != null
                && !page.getLastEditedAt().isAfter(existing.get().getNotionLastEditedAt())) {
            log.debug("Skipping import of {}: notionLastEditedAt unchanged", notionPageId);
            return new ImportResult(existing.get(), false, false);
        }

        Issue issue = existing.orElseGet(() -> Issue.builder()
                .id(UUID.randomUUID())
                .newsletterId(newsletter.getId())
                .notionPageId(notionPageId)
                .status(IssueStatus.DRAFT)
                .version(0)
                .build());

        int newVersion = (issue.getVersion() != null ? issue.getVersion() : 0) + 1;
        issue.setVersion(newVersion);
        issue.setTitle(page.getTitle() != null ? page.getTitle() : "Untitled");
        issue.setSummary(page.getSummary());
        issue.setNotionLastEditedAt(page.getLastEditedAt());

        if (issue.getSlug() == null || issue.getSlug().isBlank()) {
            issue.setSlug(slugService.resolve(newsletter.getId(), page.getSlug(), issue.getTitle()));
        }

        // Render HTML and upload
        String html = htmlRenderer.renderDocument(page);
        String htmlKey = s3Uploader.uploadHtml(newsletter.getSlug(), issue.getId(), newVersion, html);
        issue.setHtmlS3Key(htmlKey);

        // State transitions based on Notion properties
        boolean justPublished = false;
        if (page.isPublished()) {
            if (page.getScheduledAt() != null && page.getScheduledAt().isAfter(Instant.now())) {
                issue.setStatus(IssueStatus.SCHEDULED);
                issue.setScheduledAt(page.getScheduledAt());
            } else {
                if (issue.getStatus() != IssueStatus.PUBLISHED) justPublished = true;
                issue.setStatus(IssueStatus.PUBLISHED);
                issue.setPublishedAt(Instant.now());
                issue.setScheduledAt(page.getScheduledAt());
            }
        } else {
            issue.setStatus(IssueStatus.DRAFT);
            issue.setScheduledAt(page.getScheduledAt());
        }

        Issue saved = issueRepository.save(issue);

        if (justPublished) {
            publishIssuePublishedEvent(saved, newsletter);
        } else if (existing.isPresent()) {
            outboxEventService.createEvent(
                    "content.issue.updated",
                    "Issue",
                    saved.getId(),
                    Map.of(
                            "newsletterId", newsletter.getId().toString(),
                            "issueId", saved.getId().toString(),
                            "version", saved.getVersion()
                    ));
        }

        return new ImportResult(saved, existing.isEmpty(), justPublished);
    }

    private void publishIssuePublishedEvent(Issue issue, Newsletter newsletter) {
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
                        "htmlS3Key", issue.getHtmlS3Key() != null ? issue.getHtmlS3Key() : "",
                        "publishedAt", issue.getPublishedAt() != null ? issue.getPublishedAt().toString() : Instant.now().toString(),
                        "planIds", planIds.stream().map(UUID::toString).toList()
                ));
    }

    public record ImportResult(Issue issue, boolean created, boolean justPublished) {}
}
