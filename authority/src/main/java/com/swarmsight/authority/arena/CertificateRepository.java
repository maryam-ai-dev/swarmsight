package com.swarmsight.authority.arena;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CertificateRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<Stored> mapper;

    public CertificateRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new Stored(
                new Certificate(
                        rs.getString("id"),
                        rs.getString("agent_id"),
                        rs.getString("assurance_case_ref"),
                        read(rs.getString("certified_actions"), new TypeReference<List<String>>() {}),
                        read(rs.getString("not_certified_actions"), new TypeReference<List<String>>() {}),
                        rs.getString("ceiling"),
                        rs.getString("builder"),
                        rs.getString("approver"),
                        rs.getObject("issued_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        rs.getString("status")),
                readTree(rs.getString("arena_summary")));
    }

    /** A stored certificate plus the arena summary that backs it, as raw JSON. */
    public record Stored(Certificate certificate, JsonNode arenaSummary) {
    }

    public void insert(Certificate c, Object arenaSummary) {
        jdbc.update(
                "INSERT INTO certificates (id, agent_id, assurance_case_ref, certified_actions, "
                        + "not_certified_actions, ceiling, builder, approver, arena_summary, issued_at, "
                        + "expires_at, status) VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, "
                        + "CAST(? AS jsonb), ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                c.id(), c.agentId(), c.assuranceCaseRef(), write(c.certifiedActions()),
                write(c.notCertifiedActions()), c.ceiling(), c.builder(), c.approver(), write(arenaSummary),
                OffsetDateTime.ofInstant(c.issuedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(c.expiresAt(), ZoneOffset.UTC), c.status());
    }

    public Optional<Stored> findLatestByAgent(String agentId) {
        return jdbc.query("SELECT * FROM certificates WHERE agent_id = ? ORDER BY issued_at DESC LIMIT 1",
                mapper, agentId).stream().findFirst();
    }

    /** The ids of all active certificates. A policy change impacts these. */
    public List<String> findActiveIds() {
        return jdbc.queryForList("SELECT id FROM certificates WHERE status = 'ACTIVE'", String.class);
    }

    /** Flag a certificate as needing re-review after a policy change. */
    public void markReviewRequired(String id) {
        jdbc.update("UPDATE certificates SET status = 'REVIEW_REQUIRED' WHERE id = ?", id);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise certificate", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read certificate", e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read arena summary", e);
        }
    }
}
