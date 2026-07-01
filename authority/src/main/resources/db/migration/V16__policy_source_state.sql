-- Watched policy sources and the last content we saw at each, so the poller can
-- detect a change (a new content hash) and ingest only then -- it does not call
-- Claude on every poll, only when the source actually moves. A mutable working
-- table; the immutable proof of any resulting change still lands in the ledger.
CREATE TABLE policy_source_state (
    uri               TEXT        PRIMARY KEY,
    policy_id         TEXT        NOT NULL,
    last_content_hash TEXT,
    last_checked_at   TIMESTAMPTZ,
    last_changed_at   TIMESTAMPTZ
);
