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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueImportServiceTest {

    @Mock private NotionPort notionPort;
    @Mock private IssueRepository issueRepository;
    @Mock private NewsletterRepository newsletterRepository;
    @Mock private HtmlRenderer htmlRenderer;
    @Mock private S3ContentUploader s3Uploader;
    @Mock private OutboxEventService outboxEventService;
    @Mock private SlugService slugService;

    @InjectMocks private IssueImportService service;

    private Newsletter newsletter;

    @BeforeEach
    void setUp() {
        newsletter = Newsletter.builder()
                .id(UUID.randomUUID())
                .slug("tech")
                .name("Tech")
                .notionDatabaseId("db-1")
                .build();
    }

    private NotionPage page(Instant lastEdited, boolean published, Instant scheduledAt) {
        return NotionPage.builder()
                .pageId("p-1")
                .databaseId("db-1")
                .title("Edição de Abril")
                .slug("edicao-de-abril")
                .summary("resumo")
                .published(published)
                .scheduledAt(scheduledAt)
                .lastEditedAt(lastEdited)
                .blocks(List.of())
                .build();
    }

    @Test
    void skipsImportWhenNotionLastEditedUnchanged() {
        Instant same = Instant.parse("2026-04-17T12:00:00Z");
        Issue existing = Issue.builder()
                .id(UUID.randomUUID())
                .newsletterId(newsletter.getId())
                .notionPageId("p-1")
                .title("Old").slug("old")
                .status(IssueStatus.PUBLISHED)
                .version(3)
                .notionLastEditedAt(same)
                .build();

        when(notionPort.fetchPage("p-1")).thenReturn(Optional.of(page(same, true, null)));
        when(newsletterRepository.findByNotionDatabaseId("db-1")).thenReturn(Optional.of(newsletter));
        when(issueRepository.findByNotionPageId("p-1")).thenReturn(Optional.of(existing));

        IssueImportService.ImportResult result = service.importByPageId("p-1");

        assertThat(result.justPublished()).isFalse();
        assertThat(result.created()).isFalse();
        verify(issueRepository, never()).save(any());
        verify(s3Uploader, never()).uploadHtml(any(), any(), anyInt(), any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void firstImportOfPublishedPageEmitsIssuePublishedEvent() {
        when(notionPort.fetchPage("p-1")).thenReturn(Optional.of(
                page(Instant.parse("2026-04-17T12:00:00Z"), true, null)));
        when(newsletterRepository.findByNotionDatabaseId("db-1")).thenReturn(Optional.of(newsletter));
        when(issueRepository.findByNotionPageId("p-1")).thenReturn(Optional.empty());
        when(slugService.resolve(eq(newsletter.getId()), any(), any())).thenReturn("edicao-de-abril");
        when(htmlRenderer.renderDocument(any())).thenReturn("<html/>");
        when(s3Uploader.uploadHtml(eq("tech"), any(UUID.class), eq(1), eq("<html/>")))
                .thenReturn("content/tech/xxx/v1/index.html");
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
        when(newsletterRepository.findPlanIdsByNewsletterId(newsletter.getId()))
                .thenReturn(List.of(UUID.randomUUID()));

        IssueImportService.ImportResult result = service.importByPageId("p-1");

        assertThat(result.created()).isTrue();
        assertThat(result.justPublished()).isTrue();
        assertThat(result.issue().getStatus()).isEqualTo(IssueStatus.PUBLISHED);
        assertThat(result.issue().getVersion()).isEqualTo(1);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(
                eq("content.issue.published"), eq("Issue"), eq(result.issue().getId()), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("newsletterSlug", "tech")
                .containsEntry("slug", "edicao-de-abril")
                .containsEntry("htmlS3Key", "content/tech/xxx/v1/index.html")
                .containsKey("planIds");
    }

    @Test
    void subsequentEditOfPublishedIssueEmitsIssueUpdatedEvent() {
        Instant older = Instant.parse("2026-04-16T12:00:00Z");
        Instant newer = Instant.parse("2026-04-17T12:00:00Z");
        Issue existing = Issue.builder()
                .id(UUID.randomUUID())
                .newsletterId(newsletter.getId())
                .notionPageId("p-1")
                .title("Old").slug("old")
                .status(IssueStatus.PUBLISHED)
                .publishedAt(older)
                .version(1)
                .notionLastEditedAt(older)
                .build();

        when(notionPort.fetchPage("p-1")).thenReturn(Optional.of(page(newer, true, null)));
        when(newsletterRepository.findByNotionDatabaseId("db-1")).thenReturn(Optional.of(newsletter));
        when(issueRepository.findByNotionPageId("p-1")).thenReturn(Optional.of(existing));
        when(htmlRenderer.renderDocument(any())).thenReturn("<html/>");
        when(s3Uploader.uploadHtml(eq("tech"), eq(existing.getId()), eq(2), any()))
                .thenReturn("content/tech/" + existing.getId() + "/v2/index.html");
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));

        IssueImportService.ImportResult result = service.importByPageId("p-1");

        assertThat(result.created()).isFalse();
        assertThat(result.justPublished()).isFalse();
        assertThat(result.issue().getVersion()).isEqualTo(2);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(
                eq("content.issue.updated"), eq("Issue"), eq(existing.getId()), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("version", 2)
                .containsEntry("issueId", existing.getId().toString());
        verify(outboxEventService, never()).createEvent(eq("content.issue.published"), any(), any(), any());
    }

    @Test
    void futureScheduledPageTransitionsToScheduledWithoutPublishEvent() {
        Instant future = Instant.now().plusSeconds(3600);
        when(notionPort.fetchPage("p-1")).thenReturn(Optional.of(
                page(Instant.now(), true, future)));
        when(newsletterRepository.findByNotionDatabaseId("db-1")).thenReturn(Optional.of(newsletter));
        when(issueRepository.findByNotionPageId("p-1")).thenReturn(Optional.empty());
        when(slugService.resolve(any(), any(), any())).thenReturn("edicao-de-abril");
        when(htmlRenderer.renderDocument(any())).thenReturn("<html/>");
        when(s3Uploader.uploadHtml(any(), any(), anyInt(), any())).thenReturn("k");
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));

        IssueImportService.ImportResult result = service.importByPageId("p-1");

        assertThat(result.issue().getStatus()).isEqualTo(IssueStatus.SCHEDULED);
        assertThat(result.issue().getScheduledAt()).isEqualTo(future);
        assertThat(result.justPublished()).isFalse();
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void unmappedDatabaseThrows() {
        when(notionPort.fetchPage("p-1")).thenReturn(Optional.of(
                page(Instant.now(), true, null)));
        when(newsletterRepository.findByNotionDatabaseId("db-1")).thenReturn(Optional.empty());

        assertThatCode(() -> service.importByPageId("p-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable> assertThatCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable c) {
        return org.assertj.core.api.Assertions.assertThatCode(c);
    }
}
