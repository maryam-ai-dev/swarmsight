-- Source readiness snapshots the gate reads, and the service-owner deployment
-- approvals it records. See DECISIONS.md Sprint 7. The deep readiness scanner is
-- deferred; these snapshots are seeded data with the same shape the gate needs.

CREATE TABLE source_readiness (
    source_id   TEXT        PRIMARY KEY,
    connector   TEXT        NOT NULL,
    score       INT         NOT NULL,
    threshold   INT         NOT NULL,
    flags       JSONB       NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE deployment_approvals (
    id               TEXT        PRIMARY KEY,
    agent_id         TEXT        NOT NULL,
    approver         TEXT        NOT NULL,
    scope            TEXT        NOT NULL,
    trial_period     TEXT        NOT NULL,
    review_checkpoint TEXT       NOT NULL,
    conditions       JSONB       NOT NULL,
    granted_ceiling  TEXT        NOT NULL,
    approved_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_deployment_approvals_agent ON deployment_approvals (agent_id);

-- Seed the housing example sources, all above their thresholds.
INSERT INTO source_readiness (source_id, connector, score, threshold, flags, snapshot_at) VALUES
    ('case-system', 'mock-case-system', 92, 85, '[]'::jsonb, '2026-06-28T09:00:00Z'),
    ('document-store', 'mock-case-system', 86, 85, '[]'::jsonb, '2026-06-28T09:00:00Z'),
    ('policy-library', 'mock-case-system', 90, 85, '[]'::jsonb, '2026-06-28T09:00:00Z');
