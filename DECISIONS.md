# DECISIONS

Choices that are expensive to change later. Locked before code is written.
One section per sprint. Do not silently change a locked decision; add a dated
note explaining the change instead.

House rule everywhere: no em dashes or en dashes used as dashes. Plain hyphens
and rewritten sentences only. A CI check enforces this.

## Sprint 0: scaffold and guardrails

Locked 2026-06-29.

- Repository layout: monorepo. One git repo with two service folders,
  `authority/` and `intelligence/`, plus shared infra at the root
  (docker-compose, CI, scripts). Simpler for a solo founder and keeps the two
  services versioned together.

- Database: PostgreSQL 16, run locally via Docker Compose. One database named
  `swarmsight` for Authority.

- Migration tool: Flyway. SQL-first migrations under
  `authority/src/main/resources/db/migration`, named `V<n>__<description>.sql`.
  Chosen over Liquibase because plain SQL migrations are explicit and easy to
  reason about, which matters for an append-only, hash-chained ledger later.

- Canonical JSON library for Authority: Jackson (`com.fasterxml.jackson`),
  which ships with Spring Boot. The ledger's hash determinism depends on one
  canonical serialisation, locked now even though the ledger is built in
  Sprint 1. The canonical settings, to be applied wherever a hash input is
  serialised:
  - Sort map keys alphabetically (`SORT_PROPERTIES_ALPHABETICALLY` and
    `ORDER_MAP_ENTRIES_BY_KEYS`).
  - No insignificant whitespace (compact output, no pretty printing).
  - Timestamps as RFC3339 in UTC with fixed millisecond precision, serialised
    as strings, never as epoch numbers.
  - UTF-8 encoding.
  These mirror the canonical_json rules the Sprint 1 hash recipe will require,
  so the two cannot drift.

- Build tools: Authority uses Maven (Java 21, Spring Boot 3.x). Intelligence
  uses pip with a pinned `requirements.txt` (Python 3.12, FastAPI).

- CI: GitHub Actions. One workflow builds and tests both services and runs the
  dash check.
