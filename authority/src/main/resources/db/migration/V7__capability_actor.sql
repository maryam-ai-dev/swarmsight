-- Record which agent a capability was issued to, so a source_fetch ledger row
-- can attribute the access for the audit trail.
ALTER TABLE capabilities ADD COLUMN actor TEXT NOT NULL DEFAULT 'unknown';
