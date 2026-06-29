-- The agent-level containment flag. An incident records the agent here; the
-- broker refuses every fetch by a flagged agent, even a capability that escaped
-- the incident's revocation snapshot. Re-certification lifts it. This makes
-- containment airtight on the fetch path, not only on issuance.
CREATE TABLE suspended_agents (
    agent_id     TEXT        PRIMARY KEY,
    reason       TEXT        NOT NULL,
    suspended_at TIMESTAMPTZ NOT NULL
);
