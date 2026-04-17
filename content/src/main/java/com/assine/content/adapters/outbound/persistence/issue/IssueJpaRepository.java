package com.assine.content.adapters.outbound.persistence.issue;

import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.model.IssueStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueJpaRepository extends JpaRepository<Issue, UUID> {

    Optional<Issue> findByNotionPageId(String notionPageId);
    List<Issue> findByNewsletterId(UUID newsletterId);
    List<Issue> findByNewsletterIdAndStatus(UUID newsletterId, IssueStatus status);
    boolean existsBySlugAndNewsletterId(String slug, UUID newsletterId);

    @Query("SELECT i FROM Issue i WHERE i.status = 'SCHEDULED' AND i.scheduledAt <= :now ORDER BY i.scheduledAt ASC")
    List<Issue> findDueForPublishing(@Param("now") Instant now, Pageable page);

    @Query("SELECT MAX(i.notionLastEditedAt) FROM Issue i WHERE i.newsletterId = :newsletterId")
    Optional<Instant> findLatestNotionEditedAt(@Param("newsletterId") UUID newsletterId);
}
