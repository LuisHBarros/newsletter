package com.assine.content.domain.issue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issues", uniqueConstraints = {
        @UniqueConstraint(name = "uq_issue_slug_per_newsletter", columnNames = {"newsletter_id", "slug"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "newsletter_id", nullable = false)
    private UUID newsletterId;

    @Column(name = "notion_page_id", nullable = false, unique = true, length = 128)
    private String notionPageId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private IssueStatus status = IssueStatus.DRAFT;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "notion_last_edited_at")
    private Instant notionLastEditedAt;

    @Column(name = "html_s3_key", length = 512)
    private String htmlS3Key;

    @Column(name = "cover_image_s3_key", length = 512)
    private String coverImageS3Key;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
