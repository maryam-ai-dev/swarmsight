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

### Sprint 0: scaffold and guardrails (current)

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

## Running tests

Authority:

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
