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

## Sprint 1: the ledger and the verdict path

Locked 2026-06-29. The ledger is unforgiving to retrofit, so these are fixed.

### Hash recipe, locked forever

Each LedgerRow is chained to the one before it.

```
row_hash = sha256_hex(prev_hash + "|" + canonical_json(hash_input))
```

where `hash_input` is the object with exactly these nine fields:

```
{ action, actor, case_ref, intent, payload_hash, policy_version, run_id, seq, ts }
```

and:

- `canonical_json` is the one canonical serialisation locked in Sprint 0:
  keys sorted alphabetically, no whitespace, UTF-8. Because keys are sorted,
  the order the fields are listed above does not affect the bytes.
- `payload_hash = sha256_hex(canonical_json(payload))`, where `payload` is the
  decision payload (effect, reason_code, review_brief, inputs). The full
  payload is stored in the row; the hash binds it.
- `seq` is serialised as a JSON number.
- `ts` is a string in RFC3339 UTC with fixed millisecond precision, formatted
  `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`. The timestamp is truncated to milliseconds
  before it is stored and before it is hashed, so a row read back from the
  database recomputes to the same hash.
- `sha256_hex` is the lowercase hex SHA-256 of the UTF-8 bytes.

### Genesis

The first row has `seq = 1` and `prev_hash` equal to the fixed constant

```
0000000000000000000000000000000000000000000000000000000000000000
```

(64 zeros). There is no separate physical genesis row; the constant is the
chain's anchor.

### seq allocation

`seq` is allocated by a single serialised writer, not a bare sequence (a bare
sequence leaves gaps on rollback). Every append takes a Postgres transaction
advisory lock (`pg_advisory_xact_lock`, key `8157346`), reads the current
maximum `seq`, and writes `max + 1`. The lock auto-releases at commit. A
`PRIMARY KEY` on `seq` and a `UNIQUE` on `row_hash` are backstops. Result: no
gaps and no races.

### Append-only enforcement

`ledger_rows` rejects UPDATE, DELETE, and TRUNCATE at the database level via
triggers that raise an exception. Enforced in the database, not just the
application, so it holds no matter what connects.

### Idempotency

A DecisionRequest carries a `request_id`, stored `UNIQUE` on the row. A repeat
does not double-write or double-decide: the original Verdict is returned. The
check is made inside the advisory-locked critical section, and the UNIQUE
constraint is the final backstop against a concurrent duplicate.

### Fail-closed mapping (never allow)

Every one of these resolves to hold or block and is still written to the ledger,
because every decision is a ledger event:

- Unknown action (not in the policy's known actions): BLOCK, `UNKNOWN_ACTION`.
- Missing or unresolvable policy (unknown workflow): BLOCK, `POLICY_UNRESOLVABLE`.
- Missing or expired certificate: HOLD, `CERTIFICATE_INVALID`. The certificate
  is stubbed present for Sprint 1, but the check is wired into the path.
- A required input absent (policy requires `tenancy_status`): HOLD,
  `REQUIRED_INPUT_ABSENT`.
- Any internal error in the decide path: HOLD, `INTERNAL_ERROR`. This is the one
  case that may not produce a row, because the failure can be the write itself;
  the caller still receives hold, never allow.

Structural envelope fields (`request_id`, `run_id`, `case_ref`, `actor`,
`workflow`, `action`) are required by request validation and rejected with 400
if absent. They identify the run, so a malformed envelope is not a decision.

### Hardcoded policy for Sprint 1

One workflow `HA-09`, policy_version `v7`, one known action `draft_response`,
one required input `tenancy_status`, one guard: if `eviction_risk` and
`dependent_children` are both true, HOLD with reason `EVICTION_RISK_DEPENDENTS`.
Otherwise ALLOW. Policy becomes versioned data in Sprint 2; this is a stand-in.

### CORS

Authority allows cross-origin GET and POST from any origin in development, so
the static demo can fetch verdicts. This is a development convenience and will
be tightened before any real deployment.
