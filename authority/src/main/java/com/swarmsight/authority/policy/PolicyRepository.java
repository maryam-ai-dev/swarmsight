package com.swarmsight.authority.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Reads versioned policy data. The resolver returns the version in force at a
 * given timestamp: the one with the latest effective_from at or before it. No
 * write methods exist here; new versions are seeded by migration in Sprint 2 and
 * created through the Workbench in Sprint 8. The table rejects mutation anyway.
 */
@Repository
public class PolicyRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<PolicyVersion> mapper;

    public PolicyRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new PolicyVersion(
                rs.getString("policy_id"),
                rs.getString("version"),
                rs.getObject("effective_from", OffsetDateTime.class).toInstant(),
                parse(objectMapper, rs.getString("required_inputs"), new TypeReference<List<String>>() {}),
                parse(objectMapper, rs.getString("action_floors"), new TypeReference<Map<String, Level>>() {}),
                parse(objectMapper, rs.getString("guards"), new TypeReference<List<Guard>>() {}));
    }

    private static <T> T parse(ObjectMapper mapper, String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable policy JSON: " + json, e);
        }
    }

    /** The version of a policy in force at the given timestamp, if any. */
    public Optional<PolicyVersion> resolve(String policyId, Instant atTime) {
        return jdbc.query(
                "SELECT * FROM policies WHERE policy_id = ? AND effective_from <= ? "
                        + "ORDER BY effective_from DESC LIMIT 1",
                mapper, policyId, OffsetDateTime.ofInstant(atTime, java.time.ZoneOffset.UTC))
                .stream().findFirst();
    }

    /** A specific version of a policy, if it exists. */
    public Optional<PolicyVersion> findVersion(String policyId, String version) {
        return jdbc.query(
                "SELECT * FROM policies WHERE policy_id = ? AND version = ?",
                mapper, policyId, version).stream().findFirst();
    }

    /** All versions of a policy, newest effective_from first. */
    public List<PolicyVersion> findVersions(String policyId) {
        return jdbc.query(
                "SELECT * FROM policies WHERE policy_id = ? ORDER BY effective_from DESC",
                mapper, policyId);
    }

    /**
     * Insert a new policy version. The Workbench compiles a candidate into one of
     * these on activation. INSERT is allowed; the table still rejects mutation, so
     * a version is never edited once written.
     */
    public void insert(PolicyVersion v) {
        jdbc.update(
                "INSERT INTO policies (policy_id, version, effective_from, required_inputs, action_floors, "
                        + "guards, created_at) VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), "
                        + "CAST(? AS jsonb), ?) ON CONFLICT (policy_id, version) DO NOTHING",
                v.policyId(), v.version(),
                OffsetDateTime.ofInstant(v.effectiveFrom(), java.time.ZoneOffset.UTC),
                write(v.requiredInputs()), write(v.actionFloors()), write(v.guards()),
                OffsetDateTime.ofInstant(Instant.now(), java.time.ZoneOffset.UTC));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise policy JSON", e);
        }
    }
}
