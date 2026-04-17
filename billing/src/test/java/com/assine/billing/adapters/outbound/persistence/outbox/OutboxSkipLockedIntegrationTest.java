package com.assine.billing.adapters.outbound.persistence.outbox;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the exact query used by {@link OutboxEventRepositoryImpl#findDueForPublishing}
 * is compatible with PostgreSQL's {@code FOR UPDATE SKIP LOCKED} semantics so
 * multiple billing replicas can poll the outbox concurrently without blocking
 * or double-claiming rows.
 */
@Testcontainers
class OutboxSkipLockedIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CLAIM_SQL =
            "SELECT id FROM outbox_events " +
            "WHERE status = 'PENDING' AND next_attempt_at <= now() " +
            "ORDER BY created_at ASC " +
            "LIMIT ? " +
            "FOR UPDATE SKIP LOCKED";

    private static HikariDataSource dataSource;

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
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void resetOutbox() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute("TRUNCATE outbox_events");
        }
    }

    @Test
    void twoConcurrentPollersClaimDisjointRows() throws SQLException {
        List<UUID> ids = insertPendingEvents(4);

        try (Connection c1 = dataSource.getConnection();
             Connection c2 = dataSource.getConnection()) {
            c1.setAutoCommit(false);
            c2.setAutoCommit(false);

            List<UUID> claimA = claim(c1, 2);
            assertThat(claimA).hasSize(2);

            List<UUID> claimB = claim(c2, 2);
            assertThat(claimB).hasSize(2);
            assertThat(claimA).doesNotContainAnyElementsOf(claimB);

            List<UUID> union = new ArrayList<>(claimA);
            union.addAll(claimB);
            assertThat(union).containsExactlyInAnyOrderElementsOf(ids);

            assertThat(claim(c2, 10)).isEmpty();

            c1.commit();
            c2.commit();
        }
    }

    @Test
    void secondPollerDoesNotBlockOnLockedRows() throws SQLException {
        insertPendingEvents(1);

        try (Connection c1 = dataSource.getConnection();
             Connection c2 = dataSource.getConnection()) {
            c1.setAutoCommit(false);
            c2.setAutoCommit(false);

            List<UUID> claimA = claim(c1, 10);
            assertThat(claimA).hasSize(1);

            long start = System.nanoTime();
            List<UUID> claimB = claim(c2, 10);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(claimB).isEmpty();
            assertThat(elapsedMs)
                    .as("second poller must not block on the first poller's row lock")
                    .isLessThan(2_000L);

            c1.rollback();
            c2.rollback();
        }
    }

    private static List<UUID> claim(Connection c, int limit) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(CLAIM_SQL)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getObject(1, UUID.class));
                }
            }
        }
        return out;
    }

    private static List<UUID> insertPendingEvents(int n) throws SQLException {
        List<UUID> ids = new ArrayList<>(n);
        Instant base = Instant.now().minusSeconds(120);
        String sql = "INSERT INTO outbox_events(" +
                "id, event_id, aggregate_type, aggregate_id, event_type, event_payload, " +
                "status, created_at, next_attempt_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < n; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                ps.setObject(1, id);
                ps.setObject(2, UUID.randomUUID());
                ps.setString(3, "Payment");
                ps.setObject(4, UUID.randomUUID());
                ps.setString(5, "billing.payment.succeeded");
                ps.setString(6, "{}");
                ps.setObject(7, java.sql.Timestamp.from(base.plusSeconds(i)));
                ps.setObject(8, java.sql.Timestamp.from(base));
                ps.executeUpdate();
            }
        }
        return ids;
    }
}
