package com.assine.subscriptions.config;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Enables Spring scheduling and ShedLock to coordinate @Scheduled jobs across
 * multiple app replicas (ECS Fargate tasks). Without this, every replica would
 * run every cron job, causing duplicate work (e.g. expiring subscriptions
 * twice, deleting the same rows, etc.).
 *
 * <p>Note: the outbox polling is <em>not</em> guarded by ShedLock because it
 * uses {@code SELECT ... FOR UPDATE SKIP LOCKED} at the row level, which
 * allows safe parallel polling across replicas.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // avoid clock drift between replicas
                        .build()
        );
    }
}
