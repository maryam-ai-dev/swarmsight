package com.swarmsight.authority.ledger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL access to the ledger. Reads, the idempotency lookup, the serialising
 * advisory lock, and inserts. No update or delete methods exist, and the
 * database would reject them anyway.
 */
@Repository
public class LedgerRepository {

    /** Advisory lock key that serialises all appends. See DECISIONS.md. */
    private static final long APPEND_LOCK_KEY = 8157346L;

    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<LedgerRow> MAPPER = (rs, n) -> new LedgerRow(
            rs.getLong("seq"),
            rs.getString("run_id"),
            rs.getString("case_ref"),
            rs.getString("intent"),
            rs.getString("actor"),
            rs.getString("action"),
            rs.getString("payload"),
            rs.getString("payload_hash"),
            rs.getString("policy_version"),
            rs.getObject("ts", OffsetDateTime.class).toInstant(),
            rs.getString("request_id"),
            rs.getString("prev_hash"),
            rs.getString("row_hash"));

    /**
     * Take the transaction advisory lock that serialises appends. Released
     * automatically when the surrounding transaction commits or rolls back.
     * Must be called inside a transaction.
     */
    public void acquireAppendLock() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, APPEND_LOCK_KEY);
    }

    public Optional<LedgerRow> findByRequestId(String requestId) {
        return jdbc.query("SELECT * FROM ledger_rows WHERE request_id = ?", MAPPER, requestId)
                .stream().findFirst();
    }

    public Optional<LedgerRow> findLast() {
        return jdbc.query("SELECT * FROM ledger_rows ORDER BY seq DESC LIMIT 1", MAPPER)
                .stream().findFirst();
    }

    public List<LedgerRow> findByRunId(String runId) {
        return jdbc.query("SELECT * FROM ledger_rows WHERE run_id = ? ORDER BY seq", MAPPER, runId);
    }

    public Optional<LedgerRow> findLatestByCaseRef(String caseRef) {
        return jdbc.query(
                "SELECT * FROM ledger_rows WHERE case_ref = ? ORDER BY seq DESC LIMIT 1",
                MAPPER, caseRef).stream().findFirst();
    }

    public List<LedgerRow> findByCaseRef(String caseRef) {
        return jdbc.query("SELECT * FROM ledger_rows WHERE case_ref = ? ORDER BY seq", MAPPER, caseRef);
    }

    /** The agent's ledger rows: where it acted, or where it is the case subject. */
    public List<LedgerRow> findByAgent(String agentId) {
        return jdbc.query(
                "SELECT * FROM ledger_rows WHERE actor = ? OR case_ref = ? ORDER BY seq DESC",
                MAPPER, agentId, agentId);
    }

    public List<LedgerRow> findByRunIdDesc(String runId) {
        return jdbc.query("SELECT * FROM ledger_rows WHERE run_id = ? ORDER BY seq", MAPPER, runId);
    }

    public List<LedgerRow> findAllOrderBySeq() {
        return jdbc.query("SELECT * FROM ledger_rows ORDER BY seq", MAPPER);
    }

    public long count() {
        Long c = jdbc.queryForObject("SELECT count(*) FROM ledger_rows", Long.class);
        return c == null ? 0 : c;
    }

    public void insert(LedgerRow row) {
        jdbc.update(
                "INSERT INTO ledger_rows (seq, run_id, case_ref, intent, actor, action, "
                        + "payload, payload_hash, policy_version, ts, request_id, prev_hash, row_hash) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.seq(), row.runId(), row.caseRef(), row.intent(), row.actor(), row.action(),
                row.payload(), row.payloadHash(), row.policyVersion(),
                OffsetDateTime.ofInstant(row.ts(), ZoneOffset.UTC),
                row.requestId(), row.prevHash(), row.rowHash());
    }
}
