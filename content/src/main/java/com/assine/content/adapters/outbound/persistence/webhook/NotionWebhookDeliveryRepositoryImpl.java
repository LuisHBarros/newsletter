package com.assine.content.adapters.outbound.persistence.webhook;

import com.assine.content.domain.webhook.model.NotionWebhookDelivery;
import com.assine.content.domain.webhook.repository.NotionWebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotionWebhookDeliveryRepositoryImpl implements NotionWebhookDeliveryRepository {

    private final NotionWebhookDeliveryJpaRepository jpa;

    @Override @Transactional
    public NotionWebhookDelivery save(NotionWebhookDelivery delivery) { return jpa.save(delivery); }

    @Override
    public Optional<NotionWebhookDelivery> findByDeliveryId(String deliveryId) {
        return jpa.findByDeliveryId(deliveryId);
    }

    @Override @Transactional
    public void markProcessed(UUID id, NotionWebhookDelivery.Status status, String error) {
        jpa.markProcessed(id, status, error, Instant.now());
    }
}
