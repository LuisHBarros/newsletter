package com.assine.content.domain.newsletter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "newsletter_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(NewsletterPlan.Id.class)
public class NewsletterPlan {

    @jakarta.persistence.Id
    @Column(name = "newsletter_id", nullable = false)
    private UUID newsletterId;

    @jakarta.persistence.Id
    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_tier", nullable = false, length = 16)
    @Builder.Default
    private AccessTier accessTier = AccessTier.FULL;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum AccessTier { FULL, PREVIEW }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        private UUID newsletterId;
        private UUID planId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id that)) return false;
            return Objects.equals(newsletterId, that.newsletterId) && Objects.equals(planId, that.planId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(newsletterId, planId);
        }
    }
}
