package com.assine.content.domain.issue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueAsset {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "original_notion_url", columnDefinition = "TEXT")
    private String originalNotionUrl;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
