package com.assine.content.application.issue;

import com.assine.content.adapters.outbound.messaging.sqs.SqsContentJobsPublisher;
import com.assine.content.application.webhook.ImportIssueJob;
import com.assine.content.domain.issue.repository.IssueRepository;
import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.model.NewsletterStatus;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import com.assine.content.domain.notion.port.NotionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotionReconciliationJobTest {

    @Mock private NewsletterRepository newsletterRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private NotionPort notionPort;
    @Mock private SqsContentJobsPublisher jobsPublisher;

    @InjectMocks private NotionReconciliationJob job;

    private Newsletter active(String slug, String db) {
        return Newsletter.builder().id(UUID.randomUUID()).slug(slug).name(slug)
                .notionDatabaseId(db).status(NewsletterStatus.ACTIVE).build();
    }

    @Test
    void enqueuesImportForEachRecentlyEditedPageSinceLastKnownEdit() {
        Newsletter n = active("tech", "db-1");
        Instant since = Instant.parse("2026-04-10T00:00:00Z");
        when(newsletterRepository.findAll()).thenReturn(List.of(n));
        when(issueRepository.findLatestNotionEditedAt(n.getId())).thenReturn(Optional.of(since));
        when(notionPort.queryRecentlyEdited("db-1", since, 50)).thenReturn(List.of(
                new NotionPort.NotionPageSummary("page-a", Instant.parse("2026-04-15T10:00:00Z")),
                new NotionPort.NotionPageSummary("page-b", Instant.parse("2026-04-16T10:00:00Z"))
        ));

        int enqueued = job.reconcile();

        assertThat(enqueued).isEqualTo(2);
        ArgumentCaptor<ImportIssueJob> captor = ArgumentCaptor.forClass(ImportIssueJob.class);
        verify(jobsPublisher, times(2)).enqueueImport(captor.capture());
        assertThat(captor.getAllValues()).extracting(ImportIssueJob::pageId)
                .containsExactly("page-a", "page-b");
        assertThat(captor.getAllValues()).extracting(ImportIssueJob::source)
                .containsOnly("reconcile");
    }

    @Test
    void skipsArchivedNewsletters() {
        Newsletter archived = Newsletter.builder()
                .id(UUID.randomUUID()).slug("old").name("old")
                .notionDatabaseId("db-2").status(NewsletterStatus.ARCHIVED).build();
        when(newsletterRepository.findAll()).thenReturn(List.of(archived));

        assertThat(job.reconcile()).isZero();
        verifyNoInteractions(notionPort);
        verifyNoInteractions(jobsPublisher);
    }

    @Test
    void usesColdStartLookbackWhenNoIssuesYet() {
        Newsletter n = active("tech", "db-1");
        when(newsletterRepository.findAll()).thenReturn(List.of(n));
        when(issueRepository.findLatestNotionEditedAt(n.getId())).thenReturn(Optional.empty());
        when(notionPort.queryRecentlyEdited(eq("db-1"), any(Instant.class), eq(50)))
                .thenReturn(List.of());

        job.reconcile();

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(notionPort).queryRecentlyEdited(eq("db-1"), since.capture(), eq(50));
        // Cold-start lookback is 30 days; assert it's in the past ~30 days (allow skew).
        Instant now = Instant.now();
        assertThat(since.getValue()).isBefore(now.minusSeconds(29 * 24 * 3600));
        assertThat(since.getValue()).isAfter(now.minusSeconds(31 * 24 * 3600));
    }

    @Test
    void notionFailureOnOneNewsletterDoesNotBlockOthers() {
        Newsletter a = active("tech", "db-1");
        Newsletter b = active("finance", "db-2");
        when(newsletterRepository.findAll()).thenReturn(List.of(a, b));
        when(issueRepository.findLatestNotionEditedAt(any())).thenReturn(Optional.of(Instant.now().minusSeconds(3600)));
        when(notionPort.queryRecentlyEdited(eq("db-1"), any(), eq(50)))
                .thenThrow(new RuntimeException("notion 500"));
        when(notionPort.queryRecentlyEdited(eq("db-2"), any(), eq(50)))
                .thenReturn(List.of(new NotionPort.NotionPageSummary("page-b1", Instant.now())));

        int enqueued = job.reconcile();

        assertThat(enqueued).isEqualTo(1);
        verify(jobsPublisher, times(1)).enqueueImport(any());
    }
}
