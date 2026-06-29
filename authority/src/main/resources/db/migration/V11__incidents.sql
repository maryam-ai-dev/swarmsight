-- Incidents and the containment they trigger. The incident record is a working
-- store; the immutable proof of each containment action lives in the ledger.
-- See DECISIONS.md Sprint 9. ("trigger" is reserved, so trigger_type.)
CREATE TABLE incidents (
    id           TEXT        PRIMARY KEY,
    agent_id     TEXT        NOT NULL,
    trigger_type TEXT        NOT NULL,
    detail       TEXT,
    status       TEXT        NOT NULL,
    reported_by  TEXT        NOT NULL,
    raised_at    TIMESTAMPTZ NOT NULL,
    contained_at TIMESTAMPTZ,
    resolved_at  TIMESTAMPTZ,
    containment  JSONB       NOT NULL
);

CREATE INDEX idx_incidents_agent ON incidents (agent_id);
