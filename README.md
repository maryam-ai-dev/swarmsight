# SwarmSight

Governed agent platform. Authority decides, Intelligence proposes, the ledger
proves it. Built one atomic sprint at a time per `swarmsight-sprint-plan.md`.

## Architecture

- `authority/` Spring Boot (Java 21). Holds the ledger, the decisions, and the
  broker. This is where verdicts are made and proven.
- `intelligence/` FastAPI (Python 3.12). The live agent under assurance: POST
  /agent/act returns a proposed action. It only proposes; Authority decides.
- PostgreSQL 16 backs Authority.

The agent never calls Intelligence directly. Authority is always the first
stop.

## What works end to end

### Hardening: close the suspended-certificate issuance gap (current)

- The verdict path reads the real certificate status at decision time instead of
  the Sprint 2 stub. A suspended, expired, or revoked certificate is now refused
  at issuance (BLOCK), so a fresh DecisionRequest mints no capability, closing the
  gap where containment held everywhere except the issuance path.
- An allow requires a present, ACTIVE certificate, the requested action in its
  certified set, and the level within its ceiling. An unreadable store fails
  closed to block. A decision whose actor holds no certificate is decided on
  policy alone (the prior behaviour), so enforcement begins once an agent is
  certified.
- The certificate status that applied is recorded on the decision's LedgerRow.
- The decision package reads certificates through a port it owns, implemented by
  an adapter in the arena package, so no package cycle is introduced.

### Sprint 9: incident response and revocation

- An incident is raised by a trigger (missed escalation, guard breach, source
  stale, confidence collapse, human report). POST `/incidents` runs containment.
- Containment is automatic and fails closed, each step a ledger event: it
  suspends the certificate, revokes the agent's live capabilities through the
  broker (which stops honouring them at once), holds the in-flight cases, and
  disables the action class, then notifies the service owner.
- The agent cannot return to live without re-certification: the go-live gate
  only promotes on an ACTIVE certificate, and re-certification reactivates it.
- POST `/agents/{id}/restrict` restricts one action class. GET `/incidents/{id}`
  is the incident's own audit pack. GET `/oversight/metrics` and GET
  `/agents/{id}/log` drive the oversight screens.
- Frontend: the Head of department oversight and Per-agent log screens render
  real metrics and run-filtered ledger rows, and the restrict and suspend
  controls are real writes. The whole demo now runs on the backend.

### Sprint 8: Policy Workbench, propose and approve

- A rule change moves through a human-approved pipeline. POST `/policy-changes`
  fetches sources (uri, version, content hash), extracts a candidate, and shows
  it as a diff. No rule goes live unreviewed.
- If two sources disagree, the change is held with a reason rather than silently
  choosing. Tested.
- POST `/policy-changes/{id}/replay` previews the change by shadow replay against
  synthetic cases, producing a "what would change" report (the section 21 case
  flips ALLOW to HOLD).
- POST `/policy-changes/{id}/activate` compiles the candidate into a new policy
  version with a future effective date, guards carrying their source provenance.
  It flags impacted certificates (status REVIEW_REQUIRED), records the transition
  rule, and writes a policy-change ledger event. Because verdicts resolve under
  the version in force at their timestamp, a decision before the change still
  audits under the old version forever. Tested.
- Frontend: the Policy versions screen shows the real staged change with its
  shadow-replay preview, and Activate is a live write that runs the supersession.

### Sprint 7: go-live gate and service-owner sign-off

- The go-live gate reads the certificate and enforces it, it does not re-decide.
  GET `/agents/{id}/go-live` checks: certificate present and unexpired, policy
  bound, sources at or above threshold, connectors healthy, and the
  human-judgement rule active. Promotion is allowed only up to the certified
  ceiling.
- A SourceReadinessSnapshot (score, threshold, flags, what it is blocked for) is
  seeded data the gate reads. GET `/sources/readiness` serves it. Sources below
  threshold block citizen-facing promotion.
- The service-owner sign-off is the first write the gate performs. POST
  `/agents/{id}/deployment-approval` records the approver, scope, trial period,
  review checkpoint, conditions, and granted ceiling, and refuses if the gate
  blocks. A valid certificate alone is not a deployment. The approval is a ledger
  event.
- Frontend: the Go-live check renders the real certificate, readiness, and
  connector status, with the sign-off as a real write. A new Source readiness
  screen renders the real snapshots.

### Sprint 6: Arena scenario runner and Certificate

- Intelligence is now the live agent: POST /agent/act returns a proposed action.
  It escalates eviction-risk cases, requests missing evidence, drafts clear
  cases, and never proposes to send an adverse decision.
- The Arena runs a housing scenario suite against an agent in shadow through the
  governed path, and scores three axes: safety (a binary gate on severe and
  catastrophic scenarios), usefulness (informs the recommended ceiling), and
  proof (the audit trail is complete). POST `/agents/{id}/arena/run`.
- On a pass, POST `/agents/{id}/certify` builds an AssuranceCase (claims linked
  to scenario evidence) and issues a Certificate with the certified and
  not-certified actions, a recommended ceiling, a named sign-off, and a 90-day
  expiry. The builder cannot be the approver. Issuance is a ledger event.
- An agent that sends an adverse decision in the forbidden scenario fails the
  safety gate and earns no certificate. Tested.
- Frontend: the Check agent, Test results, and Certificate screens render real
  Arena output and a real Certificate. The Check agent screen runs a live check
  against Intelligence.

### Sprint 5: one real connector, permission mirror, masking

- One connector adapter (a mock case system) fetches only under a brokered
  capability, returning raw values plus the source's own per-field permission.
- The permission mirror computes, per field, the intersection of the source
  permission and the department sensitivity policy: allow, mask, or deny. Either
  side can restrict; neither can widen what the other restricts.
- Masking is applied at the boundary before the record reaches the agent. The
  fetch returns National Insurance masked and medical notes denied (removed
  entirely), matching the masking screen.
- The `field_effects` (per field: source permission, policy, outcome) are
  recorded on a `source_fetch` ledger row, never the values, so an auditor can
  prove what was exposed, masked, and denied. GET `/cases/{caseRef}/field-effects`
  serves them.
- No fetch path skips the mirror: raw values are carried only by a
  package-private record, and the broker always mirrors before returning.

### Sprint 4: capability broker skeleton

- Authority issues short-lived, scoped, revocable capabilities. A capability is
  bound to one connector, one case, and one action, grants one resource scope,
  expires in minutes, and is minted only on an allow Verdict.
- POST `/cases/{caseRef}/capabilities` decides the action and, only on an allow,
  mints a capability bound to that verdict. A hold or block mints nothing.
- POST `/broker/fetch` is the only way to reach a connector. It rejects a fetch
  with no capability, an expired one, a revoked one, or one that exceeds its
  connector, case, action, or scope. All tested.
- POST `/capabilities/{id}/revoke` revokes. Issuance and revocation are both
  ledger events.
- The broker cannot be bypassed: the connector type and the grant token are
  package-private and a grant is only made by the broker after it validates, so
  no code path reaches a connector without the check. Proven by a test.
- A mock connector exercises the broker before any real connector exists.

### Sprint 3: proof pack assembly and chain verification

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
