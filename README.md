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

### Sprint 1: the ledger and the verdict path (current)

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
