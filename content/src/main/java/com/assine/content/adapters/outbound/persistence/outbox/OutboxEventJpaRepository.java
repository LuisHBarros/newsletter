package com.assine.content.adapters.outbound.persistence.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByStatus(OutboxEventStatus status);

    List<OutboxEvent> findByStatus(OutboxEventStatus status);
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.nextAttemptAt <= :now ORDER BY o.createdAt ASC")
    List<OutboxEvent> findDueForPublishing(@Param("now") Instant now, Pageable page);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = 'PUBLISHED', o.processedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    int markAsPublished(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = 'FAILED', o.lastError = :error WHERE o.id = :id")
    int markAsFailed(@Param("id") UUID id, @Param("error") String error);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.retryCount = o.retryCount + 1, o.nextAttemptAt = :nextAttemptAt, o.lastError = :error WHERE o.id = :id")
    int scheduleRetry(@Param("id") UUID id, @Param("nextAttemptAt") Instant nextAttemptAt, @Param("error") String error);
}
