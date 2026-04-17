package com.assine.subscriptions.application.outbox;

import com.zaxxer.hikari.HikariDataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulates two parallel invocations of the same scheduled job (as would
 * happen with multiple ECS replicas running {@link ExpirationScheduler} /
 * {@link OutboxProcessor#cleanupOldPublishedEvents()} simultaneously) and
 * asserts that ShedLock's JDBC lock provider — configured exactly like
 * {@link com.assine.subscriptions.config.SchedulingConfig} — grants the
 * lock to exactly one invocation.
 *
 * <p>Runs against a real PostgreSQL container: the {@code shedlock} table
 * and {@code usingDbTime()} behavior are PG-specific and cannot be
 * faithfully reproduced on H2.
 */
@Testcontainers
class ShedLockConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HikariDataSource dataSource;
    private static LockProvider lockProvider;

    @BeforeAll
    static void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setMaximumPoolSize(5);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        lockProvider = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @AfterEach
    void cleanLocks() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM shedlock");
        }
    }

    @Test
    void secondInvocationIsRejectedWhileFirstHoldsLock() {
        String jobName = "test-job-" + UUID.randomUUID();
        LockConfiguration cfg = config(jobName);

        Optional<SimpleLock> first = lockProvider.lock(cfg);
        Optional<SimpleLock> second = lockProvider.lock(cfg);

        assertThat(first).as("first invocation must acquire the lock").isPresent();
        assertThat(second).as("concurrent invocation must be rejected").isEmpty();

        // After the first one releases, a new attempt must succeed. This
        // guards against lockAtLeastFor accidentally being set in a way
        // that would freeze out legitimate subsequent runs.
        first.get().unlock();
        Optional<SimpleLock> third = lockProvider.lock(cfg);
        assertThat(third).as("lock must be reacquirable after unlock").isPresent();
        third.get().unlock();
    }

    @Test
    void twoParallelThreadsOnlyOneAcquiresLock() throws Exception {
        String jobName = "parallel-job-" + UUID.randomUUID();
        LockConfiguration cfg = config(jobName);

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                ready.countDown();
                gate.await();
                Optional<SimpleLock> l = lockProvider.lock(cfg);
                if (l.isPresent()) {
                    // Hold the lock briefly so the other thread's attempt
                    // definitively overlaps with this one.
                    try {
                        Thread.sleep(200);
                    } finally {
                        l.get().unlock();
                    }
                    return true;
                }
                return false;
            };

            Future<Boolean> f1 = pool.submit(attempt);
            Future<Boolean> f2 = pool.submit(attempt);

            // Wait for both threads to be poised before racing them.
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            gate.countDown();

            boolean one = f1.get(10, TimeUnit.SECONDS);
            boolean two = f2.get(10, TimeUnit.SECONDS);

            // XOR: exactly one invocation must have acquired the lock.
            assertThat(one ^ two)
                    .as("exactly one of two parallel invocations must acquire the lock " +
                            "(t1=%s, t2=%s)", one, two)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    private static LockConfiguration config(String name) {
        // Mirrors the values used on @SchedulerLock annotations in the app
        // (lockAtMostFor large enough to survive a replica crash; no
        // lockAtLeastFor so the unit test can re-acquire immediately).
        return new LockConfiguration(
                Instant.now(),
                name,
                Duration.ofMinutes(5),
                Duration.ZERO
        );
    }
}
