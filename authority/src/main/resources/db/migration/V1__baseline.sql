-- V1 baseline. Sprint 0 ships an empty schema on purpose: the migration tool
-- is wired and proven to run, but no real tables exist yet. The ledger and its
-- append-only table arrive in Sprint 1.
--
-- This statement is a harmless no-op that gives Flyway a migration to apply.
SELECT 1;
