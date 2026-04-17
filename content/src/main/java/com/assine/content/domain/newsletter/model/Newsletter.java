package com.assine.content.domain.newsletter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "newsletters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Newsletter {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private NewsletterStatus status = NewsletterStatus.ACTIVE;

    @Column(name = "notion_database_id", nullable = false, unique = true, length = 128)
    private String notionDatabaseId;

    @Column(name = "default_from_email")
    private String defaultFromEmail;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version")
    @Builder.Default
    private Integer version = 0;
}
