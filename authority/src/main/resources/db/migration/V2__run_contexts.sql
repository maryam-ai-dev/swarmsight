-- RunContext ties a unit of work together. Every LedgerRow carries a run_id
-- that references one of these rows, so a row can always be traced to its run.
CREATE TABLE run_contexts (
    run_id     TEXT        PRIMARY KEY,
    case_ref   TEXT        NOT NULL,
    workflow   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
