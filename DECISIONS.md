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

## Sprint 2: policy as versioned data, guards, autonomy

Locked 2026-06-29.

### Policy is never mutated

A policy change is a new version with a new `effective_from`, never an edit. The
`policies` table rejects UPDATE and DELETE at the database level, the same way
the ledger does. INSERT is allowed, because a new version is a new row. This is
what lets a decision be re-audited under the exact version that was in force
when it was made, forever.

### The level model

Five levels, ordered: `L0 < L1 < L2 < L3 < L4_HUMAN`.

Two quantities are computed for each decision:

- `required_level`: the handling authority the case demands. It starts at the
  action floor and guards can only raise it. It is clamped at `L4_HUMAN`, which
  means "a human must decide"; no input can raise a case past that meaning.
- `effective_ceiling`: the authority the agent may exercise for this case. It
  starts at the certificate ceiling (one of `L0` to `L3`) and the three
  confidence sub-scores can only lower it. Confidence never raises it above the
  certificate ceiling.

The effect follows from comparing the two:

- `required_level == L4_HUMAN` then HOLD (a human must decide).
- else `required_level <= effective_ceiling` then ALLOW.
- else (`required_level > effective_ceiling`) then HOLD (escalate: the case
  needs more authority than the agent holds here).

BLOCK is reserved for the structural failures from Sprint 1 (unresolvable
policy, unknown action) and an explicit guard denial. Guards in this sprint
raise to `L4_HUMAN`, so they hold rather than block.

### Confidence sub-scores

Three sub-scores ride in the request inputs under `confidence`: `source`,
`evidence`, and `interpretation`, each a number from 0 to 1. Each maps to a
ceiling cap (>= 0.8 caps at L3, >= 0.5 at L2, >= 0.3 at L1, else L0). The
effective ceiling is the minimum of the certificate ceiling and every present
cap. Absent sub-scores do not lower the ceiling. They can only lower, never
raise, because the ceiling is a minimum. Real confidence comes from Intelligence
in Sprint 6; until then the request carries it (or omits it).

### Certificate ceiling

Stubbed: a valid certificate grants ceiling `L2`, a missing or expired one
grants `L0`. The real certificate and its ceiling arrive in Sprint 6. The check
is already wired into the verdict path.

### Guards as data

Each guard is data: a `name`, a `when` (a list of clauses, all of which must
hold, each clause a `key` plus an `op` of `IS_TRUE` or `IS_ABSENT`), a `raiseTo`
level, a `reasonCode`, a `brief`, and a `source` for provenance. HA-09 v7
carries two guards: eviction risk with dependent children, and eviction risk
with the latest eviction notice missing. Both raise to `L4_HUMAN`.

### Caching

No caching of policy resolution yet. Each decision resolves the version in force
at its own timestamp with a single indexed query. Caching can come later with a
key that includes the effective timestamp; doing it now would add a correctness
risk for no measured gain.

## Sprint 3: proof pack assembly and chain verification

Locked 2026-06-29.

### Three capture intents

Beyond the `decision` intent, work is captured as three more ledger intents,
written through the same single serialised, hash-chained appender:

- `author`: the service authored a draft. Payload carries the draft text.
- `edit`: a human edited the draft. Payload carries the new draft text. Written
  right after the author row, so in an uninterrupted case its prev_hash is the
  author row's row_hash.
- `approve`: an officer approved. Payload carries the officer's `reason_code`, a
  free-text `note`, and the redress fields `appeal_route` and `signposting`.
  These are distinct from the draft and the diff.

Capture is idempotent on `request_id` like every other write.

### The draft-to-final diff is derived, never stored

The diff is computed at assembly time from the author row's draft and the final
(latest edit) draft, with a word-level longest-common-subsequence. It is never
stored, so it can never disagree with the rows it is derived from.

### Chain verification

Verification recomputes, for every row in seq order: the payload_hash from the
stored payload, the row_hash from the stored fields, and the prev_hash link to
the row before it (the first row links to the genesis constant). The first
mismatch is reported with its seq and a reason. An intact chain verifies; any
tampering, to a payload, a hash, or a link, fails loudly. The proof pack shows
the live result of verifying the whole ledger at the top.

### The seven sections

The proof pack assembles seven sections from real rows, mirroring the audit
screen:

1. What happened: a plain narrative of the decision and the human role.
2. Why review was required: the reason code, brief, and guards that fired with
   their source.
3. Decision responsibility: who did what (service vs officer) and the policy
   applied.
4. Decision trace: the author, edit, and approve events with actor and time.
5. Where human judgement entered: the draft-to-final diff and the redress trail
   (appeal route, where signposting was added).
6. Sources and access: the inputs the decision saw and what was missing. The
   permission mirror and masking deepen this in Sprint 5.
7. Governing rules: certificate, policy, officer, decision time, and the
   technical verification (ledger entry count, the author/edit/approve hashes,
   and the export hash).

### Export hash

Export bundles the case's ledger rows and the policy versions they reference
into one canonical JSON structure and hashes it with the same sha256 over
canonical bytes used everywhere else. Same input, same export hash, every time.

## Sprint 4: capability broker skeleton

Locked 2026-06-29.

### A capability is narrow and short-lived

A capability is bound to exactly one connector, one case, and one action, and
grants exactly one resource scope. It expires in minutes (default 300 seconds,
`swarmsight.capability.ttl-seconds`). It is minted only on an allow Verdict and
records `issued_by_verdict` (the deciding row's row_hash), so it can never grant
more than the verdict that issued it. A fetch that asks for a different
connector, case, action, or resource scope exceeds the verdict and is rejected.

### Where the revocation list lives

The `capabilities` table is the live working store and the revocation list: a
capability is revoked by setting its `revoked_at`. Every fetch loads the
capability row and checks `revoked_at IS NULL`, the expiry, and the binding,
before any connector is touched. The ledger holds the immutable proof of
issuance and revocation; the table is the fast index the broker checks on every
use.

### Issuance and revocation are ledger events

Minting a capability writes a `capability_issued` row; revoking writes a
`capability_revoked` row. Both go through the same single serialised,
hash-chained appender as every other write, and both are idempotent.

### The broker cannot be bypassed

Connector access is structurally gated. The `Connector` interface and the
`CapabilityGrant` token are package-private to the broker package, and a grant
can only be constructed inside that package, by the broker, after it has
validated a capability. No code outside the broker package can name a grant or
call a connector, so there is no path to a connector that skips the check. The
only public entry is `CapabilityBroker`, whose every fetch validates first.

## Sprint 5: one real connector, permission mirror, masking

Locked 2026-06-29.

### The first connector

A mock case system, reached through the broker like any connector. The interface
is what matters, not the target: the adapter returns a package-private RawRecord
that never leaves the broker package un-mirrored. Swapping in SharePoint via
Microsoft Graph later is an adapter change, nothing more.

### The field sensitivity map for the housing example

The department sensitivity policy, at the agent's clearance, per field:

- `applicant_name`: ALLOW
- `income`: ALLOW
- `tenancy_status`: ALLOW
- `national_insurance`: MASK
- `medical_notes`: DENY

Any field not in the map fails closed to DENY. The agent only sees a field that
is explicitly allowed.

### The permission mirror

Per field, the outcome is the intersection of two judgements: the source's own
permission for this agent, and the department sensitivity policy. The three
effects are ordered `DENY < MASK < ALLOW`, and the intersection is the more
restrictive of the two. So either side can downgrade a field, and neither can
upgrade what the other restricts.

- ALLOW: the value passes to the agent.
- MASK: the agent sees that the field exists, but the value is replaced with a
  mask. It knows the field is there, not what it holds.
- DENY: the field is removed entirely. The agent never knows it existed.

### Masking happens at the boundary, and field_effects are ledgered

The mirror runs inside the broker, after the connector returns and before the
record leaves the broker. The masked record is what the agent receives. A
`source_fetch` ledger row records the `field_effects` (per field: the source
permission, the policy, and the outcome) but never the values, so an auditor can
prove what was exposed, masked, and denied without the ledger itself holding the
data. The fetch is idempotent per capability and resource scope.

### No fetch path skips the mirror

The connector returns a package-private RawRecord, so raw values cannot leave the
broker package. The broker is the only caller, and it always runs the mirror
before producing the public, masked ConnectorRecord. There is no path that
returns unmasked source data.
