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
    private final RowMapper<PolicyVersion> mapper;

    public PolicyRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
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

    /** All versions of a policy, newest effective_from first. */
    public List<PolicyVersion> findVersions(String policyId) {
        return jdbc.query(
                "SELECT * FROM policies WHERE policy_id = ? ORDER BY effective_from DESC",
                mapper, policyId);
    }
}
