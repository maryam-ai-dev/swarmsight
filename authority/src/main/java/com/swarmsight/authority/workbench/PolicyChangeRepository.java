package com.swarmsight.authority.workbench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyChangeRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<PolicyChange> mapper;

    public PolicyChangeRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new PolicyChange(
                rs.getString("id"),
                rs.getString("policy_id"),
                rs.getString("base_version"),
                rs.getString("proposed_version"),
                read(rs.getString("sources"), new TypeReference<List<SourceDocument>>() {}),
                readCandidate(rs.getString("candidate")),
                PolicyChange.Status.valueOf(rs.getString("status")),
                rs.getString("conflict_reason"),
                instant(rs.getObject("effective_from", OffsetDateTime.class)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                instant(rs.getObject("activated_at", OffsetDateTime.class)),
                instant(rs.getObject("suggested_effective_from", OffsetDateTime.class)));
    }

    public void insert(PolicyChange c) {
        jdbc.update(
                "INSERT INTO policy_changes (id, policy_id, base_version, proposed_version, sources, candidate, "
                        + "status, conflict_reason, effective_from, created_at) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO NOTHING",
                c.id(), c.policyId(), c.baseVersion(), c.proposedVersion(), write(c.sources()),
                c.candidate() == null ? null : write(c.candidate()), c.status().name(), c.conflictReason(),
                c.effectiveFrom() == null ? null : OffsetDateTime.ofInstant(c.effectiveFrom(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(c.createdAt(), ZoneOffset.UTC));
    }

    public Optional<PolicyChange> findById(String id) {
        return jdbc.query("SELECT * FROM policy_changes WHERE id = ?", mapper, id).stream().findFirst();
    }

    public List<PolicyChange> findByPolicyId(String policyId) {
        return jdbc.query("SELECT * FROM policy_changes WHERE policy_id = ? ORDER BY created_at DESC",
                mapper, policyId);
    }

    public void saveShadowReport(String id, ShadowReplayReport report) {
        jdbc.update("UPDATE policy_changes SET shadow_report = CAST(? AS jsonb) WHERE id = ?",
                write(report), id);
    }

    public Optional<JsonNode> findShadowReport(String id) {
        List<String> rows = jdbc.queryForList(
                "SELECT shadow_report FROM policy_changes WHERE id = ? AND shadow_report IS NOT NULL",
                String.class, id);
        return rows.stream().findFirst().map(this::readTree);
    }

    /** Pre-fill the date a change suggests at activation (e.g. an ingested commencement date). */
    public void setSuggestedEffectiveFrom(String id, Instant suggested) {
        jdbc.update("UPDATE policy_changes SET suggested_effective_from = ? WHERE id = ?",
                suggested == null ? null : OffsetDateTime.ofInstant(suggested, ZoneOffset.UTC), id);
    }

    public void markActivated(String id, Instant effectiveFrom, Instant activatedAt) {
        jdbc.update("UPDATE policy_changes SET status = 'ACTIVATED', effective_from = ?, activated_at = ? "
                        + "WHERE id = ?",
                OffsetDateTime.ofInstant(effectiveFrom, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(activatedAt, ZoneOffset.UTC), id);
    }

    private static Instant instant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }

    private CandidatePolicy readCandidate(String json) {
        return json == null ? null : read(json, new TypeReference<CandidatePolicy>() {});
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise policy change", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read policy change", e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read shadow report", e);
        }
    }
}
