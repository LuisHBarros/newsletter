package com.assine.billing.adapters.outbound.persistence.outbox;

import com.assine.billing.domain.outbox.model.OutboxEvent;
import com.assine.billing.domain.outbox.model.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxEventStatus status);
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.processedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") OutboxEventStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.lastError = :error, e.retryCount = e.retryCount + 1 WHERE e.id = :id")
    void markAsFailed(@Param("id") UUID id, @Param("status") OutboxEventStatus status, @Param("error") String error);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.retryCount = e.retryCount + 1 WHERE e.id = :id")
    int incrementRetryCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.processedAt < :cutoffDate")
    int deleteByStatusAndProcessedAtBefore(@Param("status") OutboxEventStatus status, @Param("cutoffDate") java.time.Instant cutoffDate);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.retryCount = e.retryCount + 1, e.nextAttemptAt = :nextAttemptAt, e.lastError = :error WHERE e.id = :id")
    void scheduleRetry(@Param("id") UUID id, @Param("nextAttemptAt") java.time.Instant nextAttemptAt, @Param("error") String error);
}
