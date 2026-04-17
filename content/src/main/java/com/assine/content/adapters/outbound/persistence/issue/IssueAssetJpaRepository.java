package com.assine.content.adapters.outbound.persistence.issue;

import com.assine.content.domain.issue.model.IssueAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IssueAssetJpaRepository extends JpaRepository<IssueAsset, UUID> {
    List<IssueAsset> findByIssueId(UUID issueId);

    @Modifying
    @Query("DELETE FROM IssueAsset a WHERE a.issueId = :issueId")
    int deleteByIssueId(@Param("issueId") UUID issueId);
}
