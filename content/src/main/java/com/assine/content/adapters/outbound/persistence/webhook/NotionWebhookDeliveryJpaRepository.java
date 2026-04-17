package com.assine.content.adapters.outbound.persistence.webhook;

import com.assine.content.domain.webhook.model.NotionWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotionWebhookDeliveryJpaRepository extends JpaRepository<NotionWebhookDelivery, UUID> {
    Optional<NotionWebhookDelivery> findByDeliveryId(String deliveryId);

    @Modifying
    @Query("UPDATE NotionWebhookDelivery d SET d.status = :status, d.error = :error, d.processedAt = :processedAt WHERE d.id = :id")
    int markProcessed(@Param("id") UUID id,
                      @Param("status") NotionWebhookDelivery.Status status,
                      @Param("error") String error,
                      @Param("processedAt") Instant processedAt);
}
