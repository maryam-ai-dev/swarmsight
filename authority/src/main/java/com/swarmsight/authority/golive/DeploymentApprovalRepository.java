package com.swarmsight.authority.golive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DeploymentApprovalRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<DeploymentApproval> mapper;

    public DeploymentApprovalRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new DeploymentApproval(
                rs.getString("id"),
                rs.getString("agent_id"),
                rs.getString("approver"),
                rs.getString("scope"),
                rs.getString("trial_period"),
                rs.getString("review_checkpoint"),
                read(rs.getString("conditions")),
                rs.getString("granted_ceiling"),
                rs.getObject("approved_at", OffsetDateTime.class).toInstant());
    }

    public void insert(DeploymentApproval a) {
        jdbc.update(
                "INSERT INTO deployment_approvals (id, agent_id, approver, scope, trial_period, "
                        + "review_checkpoint, conditions, granted_ceiling, approved_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?) ON CONFLICT (id) DO NOTHING",
                a.id(), a.agentId(), a.approver(), a.scope(), a.trialPeriod(), a.reviewCheckpoint(),
                write(a.conditions()), a.grantedCeiling(),
                OffsetDateTime.ofInstant(a.approvedAt(), ZoneOffset.UTC));
    }

    public Optional<DeploymentApproval> findLatestByAgent(String agentId) {
        return jdbc.query("SELECT * FROM deployment_approvals WHERE agent_id = ? ORDER BY approved_at DESC LIMIT 1",
                mapper, agentId).stream().findFirst();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise conditions", e);
        }
    }

    private List<String> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read conditions", e);
        }
    }
}
