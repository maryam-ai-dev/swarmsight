package com.swarmsight.authority.broker;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The capabilities working store and revocation list. The broker reads it on
 * every fetch to confirm a capability exists, is unexpired, and is unrevoked.
 */
@Repository
public class CapabilityRepository {

    private final JdbcTemplate jdbc;

    public CapabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }

    private static final RowMapper<Capability> MAPPER = (rs, n) -> new Capability(
            rs.getString("id"),
            rs.getString("run_id"),
            rs.getString("case_ref"),
            rs.getString("action"),
            rs.getString("actor"),
            rs.getString("connector"),
            rs.getString("resource_scope"),
            rs.getString("issued_by_verdict"),
            rs.getObject("issued_at", OffsetDateTime.class).toInstant(),
            rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
            rs.getBoolean("revocable"),
            instant(rs.getObject("revoked_at", OffsetDateTime.class)),
            rs.getString("revoked_reason"));

    public Optional<Capability> findById(String id) {
        return jdbc.query("SELECT * FROM capabilities WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /** The agent's live capabilities: unrevoked and unexpired. Used by incident containment. */
    public java.util.List<Capability> findActiveByActor(String actor) {
        return jdbc.query(
                "SELECT * FROM capabilities WHERE actor = ? AND revoked_at IS NULL AND expires_at > ?",
                MAPPER, actor,
                OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
    }

    public void insert(Capability c) {
        jdbc.update(
                "INSERT INTO capabilities (id, run_id, case_ref, action, actor, connector, resource_scope, "
                        + "issued_by_verdict, issued_at, expires_at, revocable, revoked_at, revoked_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                c.id(), c.runId(), c.caseRef(), c.action(), c.actor(), c.connector(), c.resourceScope(),
                c.issuedByVerdict(),
                OffsetDateTime.ofInstant(c.issuedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(c.expiresAt(), ZoneOffset.UTC),
                c.revocable(),
                c.revokedAt() == null ? null : OffsetDateTime.ofInstant(c.revokedAt(), ZoneOffset.UTC),
                c.revokedReason());
    }

    /** Mark a capability revoked, only if it is not already revoked. */
    public void markRevoked(String id, String reason, Instant at) {
        jdbc.update(
                "UPDATE capabilities SET revoked_at = ?, revoked_reason = ? WHERE id = ? AND revoked_at IS NULL",
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC), reason, id);
    }
}
