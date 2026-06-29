# SwarmSight

Governed agent platform. Authority decides, Intelligence proposes, the ledger
proves it. Built one atomic sprint at a time per `swarmsight-sprint-plan.md`.

## Architecture

- `authority/` Spring Boot (Java 21). Holds the ledger, the decisions, and the
  broker. This is where verdicts are made and proven.
- `intelligence/` FastAPI (Python 3.12). Proposes. A stub returning a canned
  Proposal until Sprint 6. The boundary is that Authority decides, not
  Intelligence.
- PostgreSQL 16 backs Authority.

The agent never calls Intelligence directly. Authority is always the first
stop.

## What works end to end

### Sprint 3: proof pack assembly and chain verification (current)

- Work is captured as three more ledger intents: `author` (the service drafts),
  `edit` (a human edits), and `approve` (an officer approves, recording a reason,
  a note, and the redress trail). POST `/cases/{caseRef}/author`, `/edit`,
  `/approve`. Each is hash-chained and idempotent like every other write.
- GET `/cases/{caseRef}/proof-pack` assembles the seven sections from real rows,
  with the live whole-chain verification result at the top and a stable export
  hash.
- Chain verification recomputes every payload_hash, row_hash, and prev_hash link.
  It passes on an intact chain and fails loudly on a tampered payload, hash, or
  link. Proven by tests.
- The draft-to-final diff is derived from the author and final rows at assembly
  time, never stored.
- Frontend: the Proof pack screen renders from the live endpoint, all seven
  sections, with the chain-verification banner at the top.

### Sprint 2: policy as versioned data, guards, autonomy

- Policy is versioned data in the `policies` table, each version with an
  `effective_from`. The table rejects UPDATE and DELETE: a change is a new
  version, never an edit. Proven by a test.
- Authority resolves the version in force at the decision's timestamp. A
  decision dated before a version takes effect is judged under the earlier
  version. Proven by a test (HA-09 v6 before April 2025, v7 after).
- The verdict is computed from a level model: start at the action floor, guards
  raise the required level (clamped at L4_HUMAN), confidence sub-scores lower
  the certificate ceiling (never raise it), then compare. Proven by tests.
- HA-09 v7 carries the eviction-risk-with-dependent-children guard and the
  evidence-missing guard. A case with eviction risk and dependent children holds
  for an officer with a plain-English brief.
- GET `/policies/{workflow}/versions` lists the real versions with their
  effective dates and guards.
- Frontend: the Case surface shows the real review brief and the policy version
  that applied; a new Policy versions screen lists the real versions.

The level model, policy immutability, confidence rules, and caching stance are
locked in DECISIONS.md.

### Sprint 1: the ledger and the verdict path

- POST `/decide` takes a DecisionRequest, decides allow, hold, or block against
  the hardcoded HA-09 policy, writes exactly one hash-chained LedgerRow, and
  returns a Verdict.
- The ledger is append-only and hash-chained. seq is gapless, every prev_hash
  links to the row before it, and every row_hash recomputes. Proven by a test
  that writes 1000 rows concurrently.
- The database rejects UPDATE, DELETE, and TRUNCATE on `ledger_rows`. Proven by
  a test.
- Every fail-closed case (unknown action, unresolvable policy, missing or
  expired certificate, required input absent, internal error) resolves to hold
  or block, never allow. Proven by tests.
- Idempotency on `request_id`: a repeat returns the original Verdict and writes
  no second row. Proven by a test.
- Read endpoints: GET `/cases/{caseRef}/verdict` (latest verdict for a case)
  and GET `/runs/{runId}` (a RunContext and its ledger rows).
- Frontend: the demo's Case surface (HX-4471) renders a real Verdict fetched
  from Authority, read-only. The decision buttons are still inert; the command
  behind them comes in a later sprint.

The hash recipe, seq allocation, genesis, idempotency, and fail-closed mapping
are locked in DECISIONS.md.

### Sprint 0: scaffold and guardrails

- Both services build and run with a health endpoint each.
- `docker compose up` brings up Authority, Intelligence, and Postgres.
- Authority connects to Postgres and runs Flyway migrations on startup.
- Intelligence exposes `/propose`, returning a hardcoded Proposal.
- A dash check fails the build if an em dash or en dash appears in source or
  docs.
- CI builds and tests both services and runs the dash check.

## Running locally

```
docker compose up --build
```

- Authority health: http://localhost:8080/health
- Intelligence health: http://localhost:8000/health
- Intelligence propose: http://localhost:8000/propose

### Trying the verdict path

```
curl -X POST http://localhost:8080/decide -H 'Content-Type: application/json' -d '{
  "requestId": "demo-1", "runId": "run-1", "caseRef": "CASE-1",
  "actor": "agent-housing-1", "workflow": "HA-09", "action": "draft_response",
  "inputs": {"tenancy_status": "secure", "eviction_risk": true, "dependent_children": true}
}'
```

Open `swarmsight-connected-demo.html` in a browser with the stack running. The
Case surface (HX-4471) fetches and renders its real Verdict from Authority.

## Running tests

Authority (needs a running Docker daemon: the integration tests use
Testcontainers to spin up Postgres):

```
cd authority && mvn test
```

Intelligence:

```
cd intelligence && pip install -r requirements.txt && pytest
```

## Repo conventions

- No em dashes or en dashes used as dashes. Enforced by `scripts/check-dashes.sh`.
- Decisions that are expensive to change are locked in `DECISIONS.md` before
  code is written.
