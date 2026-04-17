package com.assine.content.domain.webhook.repository;

import com.assine.content.domain.webhook.model.NotionWebhookDelivery;

import java.util.Optional;
import java.util.UUID;

public interface NotionWebhookDeliveryRepository {
    NotionWebhookDelivery save(NotionWebhookDelivery delivery);
    Optional<NotionWebhookDelivery> findByDeliveryId(String deliveryId);
    void markProcessed(UUID id, NotionWebhookDelivery.Status status, String error);
}
