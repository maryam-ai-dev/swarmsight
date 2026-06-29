-- Policy as versioned data. A change is a new version with a new effective_from,
-- never an edit. See DECISIONS.md Sprint 2. The resolver reads the version in
-- force at a decision's timestamp.
CREATE TABLE policies (
    id              BIGSERIAL   PRIMARY KEY,
    policy_id       TEXT        NOT NULL,
    version         TEXT        NOT NULL,
    effective_from  TIMESTAMPTZ NOT NULL,
    required_inputs JSONB       NOT NULL,
    action_floors   JSONB       NOT NULL,
    guards          JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    UNIQUE (policy_id, version)
);

-- Resolve the version in force: highest effective_from at or before a timestamp.
CREATE INDEX idx_policies_resolve ON policies (policy_id, effective_from DESC);

-- Append-only, like the ledger: a version is never mutated, only superseded by a
-- newer one. INSERT is allowed; UPDATE and DELETE are rejected.
CREATE OR REPLACE FUNCTION policies_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'policies is versioned and never mutated: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER policies_no_update_delete
    BEFORE UPDATE OR DELETE ON policies
    FOR EACH ROW EXECUTE FUNCTION policies_reject_mutation();

CREATE TRIGGER policies_no_truncate
    BEFORE TRUNCATE ON policies
    FOR EACH STATEMENT EXECUTE FUNCTION policies_reject_mutation();
