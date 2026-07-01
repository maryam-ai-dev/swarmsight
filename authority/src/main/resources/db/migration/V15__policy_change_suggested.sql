-- The effective date a change suggests at activation. For an ingested change
-- this is the commencement date Claude read from the source (or, if that is
-- already past or unstated, a sensible future default). The service owner still
-- confirms or overrides it on activation; this only pre-fills the field.
ALTER TABLE policy_changes ADD COLUMN suggested_effective_from TIMESTAMPTZ;
