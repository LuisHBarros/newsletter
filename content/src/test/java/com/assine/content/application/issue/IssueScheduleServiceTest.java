package com.assine.content.application.issue;

import com.assine.content.application.outbox.OutboxEventService;
import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.model.IssueStatus;
import com.assine.content.domain.issue.repository.IssueRepository;
import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
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
class IssueScheduleServiceTest {

    @Mock private IssueRepository issueRepository;
    @Mock private NewsletterRepository newsletterRepository;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks private IssueScheduleService service;

    @Test
    void transitionsDueIssuesToPublishedAndEmitsEvent() {
        Newsletter n = Newsletter.builder()
                .id(UUID.randomUUID()).slug("tech").name("Tech").notionDatabaseId("db-1").build();
        Issue due = Issue.builder()
                .id(UUID.randomUUID())
                .newsletterId(n.getId())
                .notionPageId("p-1")
                .title("T").slug("t")
                .status(IssueStatus.SCHEDULED)
                .scheduledAt(Instant.now().minusSeconds(60))
                .htmlS3Key("k")
                .version(1)
                .build();

        when(issueRepository.findDueForPublishing(any(Instant.class), anyInt()))
                .thenReturn(List.of(due));
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
        when(newsletterRepository.findById(n.getId())).thenReturn(Optional.of(n));
        UUID plan = UUID.randomUUID();
        when(newsletterRepository.findPlanIdsByNewsletterId(n.getId())).thenReturn(List.of(plan));

        int count = service.publishDue();

        assertThat(count).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(IssueStatus.PUBLISHED);
        assertThat(due.getPublishedAt()).isNotNull();

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(
                eq("content.issue.published"), eq("Issue"), eq(due.getId()), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("newsletterSlug", "tech")
                .containsEntry("htmlS3Key", "k")
                .containsEntry("slug", "t");
        @SuppressWarnings("unchecked")
        List<String> planIds = (List<String>) payload.getValue().get("planIds");
        assertThat(planIds).containsExactly(plan.toString());
    }

    @Test
    void noDueIssuesIsANoop() {
        when(issueRepository.findDueForPublishing(any(Instant.class), anyInt()))
                .thenReturn(List.of());

        assertThat(service.publishDue()).isZero();
        verifyNoInteractions(outboxEventService);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void missingNewsletterThrowsAndHaltsBatch() {
        Issue due = Issue.builder()
                .id(UUID.randomUUID())
                .newsletterId(UUID.randomUUID())
                .notionPageId("p").title("T").slug("t")
                .status(IssueStatus.SCHEDULED)
                .scheduledAt(Instant.now().minusSeconds(30))
                .version(1).build();
        when(issueRepository.findDueForPublishing(any(Instant.class), anyInt())).thenReturn(List.of(due));
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
        when(newsletterRepository.findById(due.getNewsletterId())).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatCode(() -> service.publishDue())
                .isInstanceOf(IllegalStateException.class);
    }
}
