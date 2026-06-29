-- Staged policy changes moving through the Workbench pipeline: proposed,
-- reviewed by shadow replay, and activated on a future effective date. This is
-- a mutable working table; the immutable proof of activation lives in the
-- ledger. See DECISIONS.md Sprint 8.
CREATE TABLE policy_changes (
    id               TEXT        PRIMARY KEY,
    policy_id        TEXT        NOT NULL,
    base_version     TEXT        NOT NULL,
    proposed_version TEXT        NOT NULL,
    sources          JSONB       NOT NULL,
    candidate        JSONB,
    status           TEXT        NOT NULL,
    conflict_reason  TEXT,
    effective_from   TIMESTAMPTZ,
    shadow_report    JSONB,
    created_at       TIMESTAMPTZ NOT NULL,
    activated_at     TIMESTAMPTZ
);

CREATE INDEX idx_policy_changes_policy ON policy_changes (policy_id);
