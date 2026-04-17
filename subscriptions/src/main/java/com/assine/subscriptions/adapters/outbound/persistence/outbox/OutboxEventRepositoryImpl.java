package com.assine.subscriptions.adapters.outbound.persistence.outbox;

import com.assine.subscriptions.domain.outbox.model.OutboxEvent;
import com.assine.subscriptions.domain.outbox.model.OutboxEventStatus;
import com.assine.subscriptions.domain.outbox.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    /**
     * Lease applied to claimed rows: pushes {@code next_attempt_at} forward so
     * concurrent pollers (other app instances) skip these rows even after the
     * SELECT ... FOR UPDATE SKIP LOCKED transaction commits.
     */
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(60);

    private final OutboxEventJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxEventStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    public List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId) {
        return jpaRepository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void markAsPublished(UUID id) {
        jpaRepository.updateStatus(id, OutboxEventStatus.PUBLISHED);
    }

    @Override
    @Transactional
    public void markAsFailed(UUID id, String error) {
        jpaRepository.markAsFailed(id, OutboxEventStatus.FAILED, error);
    }

    @Override
    @Transactional
    public int incrementRetryCount(UUID id) {
        return jpaRepository.incrementRetryCount(id);
    }

    @Override
    @Transactional
    public int deleteOldPublishedEvents(Instant cutoffDate) {
        return jpaRepository.deleteByStatusAndProcessedAtBefore(OutboxEventStatus.PUBLISHED, cutoffDate);
    }

    /**
     * Atomically claim a batch of pending events for publishing.
     *
     * <p>Uses PostgreSQL's {@code FOR UPDATE SKIP LOCKED} so multiple app
     * instances (or scheduler threads) can poll concurrently without
     * contending on the same rows. After selecting, the claimed rows get
     * {@code next_attempt_at} pushed by {@link #CLAIM_LEASE} so that, once
     * this transaction commits and releases the row locks, other pollers
     * won't pick the same rows again until the publish finishes (which will
     * mark them as PUBLISHED) or the lease expires.
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<OutboxEvent> findDueForPublishing(int limit, Instant now) {
        List<OutboxEvent> events = entityManager.createNativeQuery(
                        "SELECT * FROM outbox_events " +
                        "WHERE status = 'PENDING' AND next_attempt_at <= :now " +
                        "ORDER BY created_at ASC " +
                        "LIMIT :limit " +
                        "FOR UPDATE SKIP LOCKED",
                        OutboxEvent.class)
                .setParameter("now", now)
                .setParameter("limit", limit)
                .getResultList();

        if (!events.isEmpty()) {
            Instant leaseUntil = now.plus(CLAIM_LEASE);
            List<UUID> ids = events.stream().map(OutboxEvent::getId).toList();
            entityManager.createQuery(
                            "UPDATE OutboxEvent e SET e.nextAttemptAt = :leaseUntil WHERE e.id IN :ids")
                    .setParameter("leaseUntil", leaseUntil)
                    .setParameter("ids", ids)
                    .executeUpdate();
        }
        return events;
    }

    @Override
    @Transactional
    public void scheduleRetry(UUID id, Instant nextAttemptAt, String error) {
        jpaRepository.scheduleRetry(id, nextAttemptAt, error);
    }
}
