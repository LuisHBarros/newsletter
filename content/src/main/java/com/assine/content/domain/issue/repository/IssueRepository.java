package com.assine.content.domain.issue.repository;

import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.model.IssueAsset;
import com.assine.content.domain.issue.model.IssueStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository {
    Issue save(Issue issue);
    Optional<Issue> findById(UUID id);
    Optional<Issue> findByNotionPageId(String notionPageId);
    List<Issue> findByNewsletterId(UUID newsletterId);
    List<Issue> findByNewsletterIdAndStatus(UUID newsletterId, IssueStatus status);
    List<Issue> findDueForPublishing(Instant now, int limit);
    Optional<Instant> findLatestNotionEditedAt(UUID newsletterId);
    boolean existsBySlugAndNewsletterId(String slug, UUID newsletterId);

    IssueAsset saveAsset(IssueAsset asset);
    List<IssueAsset> findAssetsByIssueId(UUID issueId);
    void deleteAssetsByIssueId(UUID issueId);
}
