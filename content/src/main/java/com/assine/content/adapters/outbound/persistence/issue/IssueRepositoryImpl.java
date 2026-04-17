package com.assine.content.adapters.outbound.persistence.issue;

import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.model.IssueAsset;
import com.assine.content.domain.issue.model.IssueStatus;
import com.assine.content.domain.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IssueRepositoryImpl implements IssueRepository {

    private final IssueJpaRepository issueJpa;
    private final IssueAssetJpaRepository assetJpa;

    @Override @Transactional
    public Issue save(Issue issue) { return issueJpa.save(issue); }

    @Override
    public Optional<Issue> findById(UUID id) { return issueJpa.findById(id); }

    @Override
    public Optional<Issue> findByNotionPageId(String notionPageId) {
        return issueJpa.findByNotionPageId(notionPageId);
    }

    @Override
    public List<Issue> findByNewsletterId(UUID newsletterId) {
        return issueJpa.findByNewsletterId(newsletterId);
    }

    @Override
    public List<Issue> findByNewsletterIdAndStatus(UUID newsletterId, IssueStatus status) {
        return issueJpa.findByNewsletterIdAndStatus(newsletterId, status);
    }

    @Override
    public List<Issue> findDueForPublishing(Instant now, int limit) {
        return issueJpa.findDueForPublishing(now, PageRequest.of(0, limit));
    }

    @Override
    public Optional<Instant> findLatestNotionEditedAt(UUID newsletterId) {
        return issueJpa.findLatestNotionEditedAt(newsletterId);
    }

    @Override
    public boolean existsBySlugAndNewsletterId(String slug, UUID newsletterId) {
        return issueJpa.existsBySlugAndNewsletterId(slug, newsletterId);
    }

    @Override @Transactional
    public IssueAsset saveAsset(IssueAsset asset) { return assetJpa.save(asset); }

    @Override
    public List<IssueAsset> findAssetsByIssueId(UUID issueId) {
        return assetJpa.findByIssueId(issueId);
    }

    @Override @Transactional
    public void deleteAssetsByIssueId(UUID issueId) { assetJpa.deleteByIssueId(issueId); }
}
