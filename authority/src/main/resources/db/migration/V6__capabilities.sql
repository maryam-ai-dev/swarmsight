-- Short-lived, scoped, revocable capabilities. The live working store and the
-- revocation list: a capability is revoked by setting revoked_at, which every
-- fetch checks. The immutable proof of issuance and revocation lives in the
-- ledger; this table is the fast index the broker checks on every use. See
-- DECISIONS.md Sprint 4.
CREATE TABLE capabilities (
    id                TEXT        PRIMARY KEY,
    run_id            TEXT        NOT NULL,
    case_ref          TEXT        NOT NULL,
    action            TEXT        NOT NULL,
    connector         TEXT        NOT NULL,
    resource_scope    TEXT        NOT NULL,
    issued_by_verdict TEXT        NOT NULL,
    issued_at         TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revocable         BOOLEAN     NOT NULL,
    revoked_at        TIMESTAMPTZ,
    revoked_reason    TEXT
);

CREATE INDEX idx_capabilities_case ON capabilities (case_ref);
