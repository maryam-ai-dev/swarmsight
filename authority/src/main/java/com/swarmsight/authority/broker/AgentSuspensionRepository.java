package com.swarmsight.authority.broker;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The agent-level containment flag. The broker reads it on every fetch, so a
 * suspended agent is refused even for a capability that was minted in the race
 * window and escaped an incident's revocation snapshot.
 */
@Repository
public class AgentSuspensionRepository {

    private final JdbcTemplate jdbc;

    public AgentSuspensionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void suspend(String agentId, String reason, Instant at) {
        jdbc.update(
                "INSERT INTO suspended_agents (agent_id, reason, suspended_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (agent_id) DO UPDATE SET reason = EXCLUDED.reason, "
                        + "suspended_at = EXCLUDED.suspended_at",
                agentId, reason, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    public void lift(String agentId) {
        jdbc.update("DELETE FROM suspended_agents WHERE agent_id = ?", agentId);
    }

    public boolean isSuspended(String agentId) {
        Boolean found = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM suspended_agents WHERE agent_id = ?)", Boolean.class, agentId);
        return Boolean.TRUE.equals(found);
    }
}
