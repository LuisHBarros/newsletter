package com.assine.content.application.webhook;

import com.assine.content.adapters.outbound.messaging.sqs.SqsContentJobsPublisher;
import com.assine.content.domain.webhook.model.NotionWebhookDelivery;
import com.assine.content.domain.webhook.repository.NotionWebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotionWebhookServiceTest {

    @Mock private NotionWebhookDeliveryRepository deliveryRepository;
    @Mock private SqsContentJobsPublisher jobsPublisher;

    @InjectMocks private NotionWebhookService service;

    private final Map<String, Object> validPayload = Map.of(
            "page_id", "notion-page-1",
            "last_edited_time", "2026-04-17T12:00:00Z"
    );

    @Test
    void duplicateDeliveryIdShortCircuitsWithoutEnqueue() {
        when(deliveryRepository.findByDeliveryId("dup-1"))
                .thenReturn(Optional.of(NotionWebhookDelivery.builder().build()));

        NotionWebhookService.Outcome outcome = service.handle("dup-1", validPayload);

        assertThat(outcome).isEqualTo(NotionWebhookService.Outcome.DUPLICATE);
        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(jobsPublisher);
    }

    @Test
    void validDeliveryEnqueuesImportJobAndMarksAccepted() {
        when(deliveryRepository.findByDeliveryId("d-2")).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(i -> {
            NotionWebhookDelivery d = i.getArgument(0);
            return d; // identity; markProcessed will be stubbed by default (void)
        });

        NotionWebhookService.Outcome outcome = service.handle("d-2", validPayload);

        assertThat(outcome).isEqualTo(NotionWebhookService.Outcome.ACCEPTED);
        ArgumentCaptor<ImportIssueJob> job = ArgumentCaptor.forClass(ImportIssueJob.class);
        verify(jobsPublisher).enqueueImport(job.capture());
        assertThat(job.getValue().pageId()).isEqualTo("notion-page-1");
        assertThat(job.getValue().source()).isEqualTo("webhook");
        assertThat(job.getValue().lastEditedAt()).isEqualTo(Instant.parse("2026-04-17T12:00:00Z"));
        verify(deliveryRepository).markProcessed(any(), eq(NotionWebhookDelivery.Status.ACCEPTED), any());
    }

    @Test
    void payloadMissingPageIdMarksRejected() {
        when(deliveryRepository.findByDeliveryId(any())).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NotionWebhookService.Outcome outcome =
                service.handle("d-3", Map.of("last_edited_time", "2026-04-17T12:00:00Z"));

        assertThat(outcome).isEqualTo(NotionWebhookService.Outcome.INVALID);
        verify(deliveryRepository).markProcessed(any(), eq(NotionWebhookDelivery.Status.REJECTED), any());
        verifyNoInteractions(jobsPublisher);
    }

    @Test
    void extractsPageIdFromNestedDataEntity() {
        when(deliveryRepository.findByDeliveryId(any())).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> payload = Map.of(
                "data", Map.of("page", Map.of("id", "nested-page-1")),
                "last_edited_time", "2026-04-17T12:00:00Z"
        );

        assertThat(service.handle("d-4", payload))
                .isEqualTo(NotionWebhookService.Outcome.ACCEPTED);

        ArgumentCaptor<ImportIssueJob> job = ArgumentCaptor.forClass(ImportIssueJob.class);
        verify(jobsPublisher).enqueueImport(job.capture());
        assertThat(job.getValue().pageId()).isEqualTo("nested-page-1");
    }

    @Test
    void nullDeliveryIdGeneratesOneAndStillAccepts() {
        when(deliveryRepository.findByDeliveryId(any())).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.handle(null, validPayload))
                .isEqualTo(NotionWebhookService.Outcome.ACCEPTED);

        ArgumentCaptor<NotionWebhookDelivery> captor =
                ArgumentCaptor.forClass(NotionWebhookDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryId()).isNotBlank();
    }

    @Test
    void publisherFailureMarksDeliveryFailedAndPropagates() {
        when(deliveryRepository.findByDeliveryId(any())).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("sqs down"))
                .when(jobsPublisher).enqueueImport(any());

        try {
            service.handle("d-5", validPayload);
        } catch (RuntimeException expected) {
            assertThat(expected).hasMessage("sqs down");
        }

        verify(deliveryRepository).markProcessed(any(), eq(NotionWebhookDelivery.Status.FAILED), eq("sqs down"));
    }
}
