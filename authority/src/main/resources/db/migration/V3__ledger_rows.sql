-- The ledger. Append-only and hash-chained. See DECISIONS.md Sprint 1 for the
-- hash recipe, seq allocation, genesis constant, and idempotency rules.
CREATE TABLE ledger_rows (
    seq            BIGINT      PRIMARY KEY,
    run_id         TEXT        NOT NULL REFERENCES run_contexts(run_id),
    case_ref       TEXT        NOT NULL,
    intent         TEXT        NOT NULL,
    actor          TEXT        NOT NULL,
    action         TEXT        NOT NULL,
    -- Stored as TEXT, not JSONB, on purpose: the payload_hash binds these exact
    -- canonical bytes, and JSONB would re-normalise them and break the hash.
    payload        TEXT        NOT NULL,
    payload_hash   TEXT        NOT NULL,
    policy_version TEXT        NOT NULL,
    ts             TIMESTAMPTZ NOT NULL,
    request_id     TEXT        NOT NULL UNIQUE,
    prev_hash      TEXT        NOT NULL,
    row_hash       TEXT        NOT NULL UNIQUE
);

CREATE INDEX idx_ledger_rows_run_id ON ledger_rows (run_id);
CREATE INDEX idx_ledger_rows_case_ref ON ledger_rows (case_ref);

-- Append-only enforcement at the database level. UPDATE, DELETE, and TRUNCATE
-- are all rejected, no matter which role connects.
CREATE OR REPLACE FUNCTION ledger_rows_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_rows is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_rows_no_update_delete
    BEFORE UPDATE OR DELETE ON ledger_rows
    FOR EACH ROW EXECUTE FUNCTION ledger_rows_reject_mutation();

CREATE TRIGGER ledger_rows_no_truncate
    BEFORE TRUNCATE ON ledger_rows
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_rows_reject_mutation();
