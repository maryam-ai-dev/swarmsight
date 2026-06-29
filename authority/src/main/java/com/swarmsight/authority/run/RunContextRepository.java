package com.swarmsight.authority.run;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RunContextRepository {

    private final JdbcTemplate jdbc;

    public RunContextRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<RunContext> MAPPER = (rs, n) -> new RunContext(
            rs.getString("run_id"),
            rs.getString("case_ref"),
            rs.getString("workflow"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    /**
     * Create the RunContext if it does not exist yet. Idempotent: a second call
     * for the same run_id leaves the original untouched.
     */
    public void ensureExists(String runId, String caseRef, String workflow, Instant createdAt) {
        jdbc.update(
                "INSERT INTO run_contexts (run_id, case_ref, workflow, created_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (run_id) DO NOTHING",
                runId, caseRef, workflow, OffsetDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC));
    }

    public Optional<RunContext> find(String runId) {
        return jdbc.query("SELECT * FROM run_contexts WHERE run_id = ?", MAPPER, runId)
                .stream().findFirst();
    }
}
