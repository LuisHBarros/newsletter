package com.assine.content.adapters.outbound.persistence.newsletter;

import com.assine.content.domain.newsletter.model.NewsletterPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NewsletterPlanJpaRepository extends JpaRepository<NewsletterPlan, NewsletterPlan.Id> {

    List<NewsletterPlan> findByNewsletterId(UUID newsletterId);

    @Query("SELECT np.planId FROM NewsletterPlan np WHERE np.newsletterId = :newsletterId")
    List<UUID> findPlanIdsByNewsletterId(@Param("newsletterId") UUID newsletterId);

    @Modifying
    @Query("DELETE FROM NewsletterPlan np WHERE np.newsletterId = :newsletterId")
    int deleteByNewsletterId(@Param("newsletterId") UUID newsletterId);
}
