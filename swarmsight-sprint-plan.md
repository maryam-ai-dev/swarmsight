# SwarmSight backend, sprint plan for Claude Code

A build plan that turns the backend spec into code, one atomic sprint at a time. It is written for Claude Code: each sprint is self-contained, has decisions locked before any code is written, a tight scope, and a definition of done you can actually check. Build in order. Do not start a sprint until the previous one's definition of done is green.

House rule, as everywhere: no em dashes or en dashes used as dashes.

The spine you are building, in one line: Authority first, Intelligence proposes, policy is approved and versioned, agents earn certificates, sources are checked, actions are brokered, humans decide serious cases, the ledger proves it.

---

## How to use this with Claude Code

- One sprint per session or per branch. Paste the sprint section as the brief. Keep sprints atomic so a session can finish one.
- Lock the "Decisions to make first" before writing code. These are the choices that are expensive to change later. Write the answers into a DECISIONS.md in the repo.
- Treat the definition of done as the acceptance test. If you cannot demonstrate every line, the sprint is not finished.
- Write the test in the same sprint as the code. The spec's guarantees (fail closed, no gaps in the ledger, broker not bypassable) are only real if a test proves them.
- After each sprint, update the repo README with what now works end to end.

Stack, from the spec: Authority in Spring Boot and Java with PostgreSQL, Intelligence in FastAPI and Python. Authority is where the ledger, the decisions, and the broker live. Intelligence can be a stub returning a canned Proposal until Sprint 6, because the boundary is that Authority decides, not Intelligence.

The frontend, built gradually, not as a separate phase. You already have the connected demo: 14 designed screens that are currently hardcoded HTML with no data behind them. You do not rebuild these. As each backend sprint produces something renderable, you replace one screen's hardcoded body with real data fetched from the Authority API. That is the "Wire the frontend" line at the end of the relevant sprints below. By the end, the same demo you have is running on a live backend, screen by screen, with nothing thrown away. Keep every screen read-only against the API first (render real objects), and only wire the write actions (approve, restrict, activate) in the sprint that builds the command behind them.

---

## Sprint 0: scaffold and guardrails

**Goal.** A running skeleton of both services, a database, and the test and CI setup, with nothing real in it yet.

**Decisions to make first.**
- Monorepo or two repos. A monorepo is simpler for a solo founder.
- Postgres locally via Docker Compose. Decide the migration tool (Flyway or Liquibase for Spring).
- One canonical JSON library and serialisation setting for Authority, chosen now, because the ledger's determinism depends on it later.

**Build tasks.**
- Authority: Spring Boot app, health endpoint, Postgres connection, migration tool wired, one empty migration.
- Intelligence: FastAPI app, health endpoint, a `/propose` route that returns a hardcoded Proposal.
- Docker Compose bringing up Authority, Intelligence, and Postgres.
- CI that builds both services and runs tests.
- A repo check that fails the build if an em dash or en dash appears in source or docs.

**Definition of done.**
- `docker compose up` brings up both services and the database.
- Both health endpoints return ok.
- CI is green on an empty test suite.
- The dash check runs in CI.

---

## Sprint 1: the ledger and the verdict path (the spine)

This is the most important sprint. Everything downstream trusts the ledger, and it is unforgiving to retrofit. Build it correctly here or pay tenfold later.

**Goal.** Authority accepts a DecisionRequest, decides allow or hold against a hardcoded policy, writes a hash-chained LedgerRow, and returns a Verdict. A RunContext ties the work together.

**Decisions to make first, and write into DECISIONS.md.**
- The exact hash recipe, locked forever. Recommended: `row_hash = sha256(prev_hash + "|" + canonical_json(seq, run_id, case_ref, intent, actor, action, payload_hash, policy_version, ts))`. The canonical_json must sort keys, use no whitespace, fix number and timestamp formatting (timestamps as RFC3339 in UTC with fixed precision), and be identical across languages.
- How `seq` is allocated. Use a single serialised writer or a Postgres sequence with a uniqueness constraint, so there are no gaps and no races.
- Genesis row: the first row's prev_hash is a fixed constant, recorded in DECISIONS.md.
- Idempotency: a DecisionRequest carries a `request_id`, and a repeat must not double-write or double-decide.

**Build tasks.**
- Objects and tables: RunContext, DecisionRequest, Verdict, LedgerRow. Thread `run_id` through all of them.
- A `ledger_rows` table that is append-only: no update, no delete, enforced at the database level (revoke update and delete, or use a trigger that rejects them).
- The decide endpoint: accept a DecisionRequest, look up a hardcoded policy (one workflow, one action floor, one guard), compute a Verdict, write a LedgerRow, return the Verdict. No Intelligence call yet, or call the stub.
- Fail closed: enumerate and handle every case below so each returns hold or block, never allow.
- Idempotency on `request_id`.

**Fail-closed cases to implement and test (all resolve to hold or block).**
- Unknown action.
- Missing or unresolvable policy.
- Expired or missing certificate (stub the certificate as present for now, but wire the check).
- A required input absent.
- Any internal error in the decide path.

**Definition of done.**
- Posting a DecisionRequest returns a Verdict and writes exactly one LedgerRow.
- A test writes 1000 rows concurrently and proves: no gaps in `seq`, every `prev_hash` links, every `row_hash` recomputes.
- A test proves update and delete on `ledger_rows` are rejected by the database.
- A test proves each fail-closed case returns hold or block, never allow.
- A test proves a repeated `request_id` does not double-write.
- You can explain, from the code, which RunContext a given LedgerRow belongs to.
- **Wire the frontend:** point the Case surface at the live decide endpoint so it renders a real Verdict (effect and review brief) instead of hardcoded text. Read-only, no buttons wired yet.

---

## Sprint 2: policy as versioned data, guards, autonomy

**Goal.** The verdict becomes real. Policy is versioned data with effective dates. Authority resolves the version in force at the decision timestamp and computes the level from action floor, guards, and confidence.

**Decisions to make first.**
- Policy is never mutated. A change is a new version with a new `effective_from`. Lock this.
- The level model: L0 to L3 as the certificate ceiling, L4_human as an action property. Confidence (the three sub-scores) can only lower, never raise.
- Cache policy resolution with a cache key that includes the effective timestamp, or do not cache yet.

**Build tasks.**
- Policy object and table, versioned, with `action_floors` and `guards` (each guard with `when`, `effect`, `source`).
- Resolver: given a workflow and a timestamp, return the version in force then.
- Verdict computation: start at the action floor, apply guards (raise only), apply the three confidence sub-scores (lower only), compare to the certificate ceiling, return effect allow, hold, or block with a review brief.
- Encode the housing example: HA-09 v7 with the eviction-risk-plus-dependent-children guard and the evidence-missing guard.

**Definition of done.**
- A case with eviction risk and dependent children resolves to hold with a plain-English review brief, under HA-09 v7.
- A decision dated before a version's `effective_from` is judged under the earlier version, proven by test.
- A guard can raise the level but no input can raise it above L4_human's meaning, and confidence can only lower. Tested.
- Policy rows are never updated, proven by test.
- **Wire the frontend:** the Case surface now shows the real review brief and the policy version that applied, pulled from the Verdict. The Policy versions screen lists real Policy versions with their effective dates.

---

## Sprint 3: proof pack assembly and chain verification

Built early on purpose. Assembling the pack forces ledger-schema gaps into the open before they harden, and the trace is the product's credibility.

**Goal.** Read all LedgerRows for a case, grouped by run, verify the chain, and assemble the proof pack the audit screen renders.

**Build tasks.**
- The author, edit, approve capture: three LedgerRow intents, with the edit row's prev_hash pointing at the author row.
- The approve row carries the officer's reason (reason_code and a free-text note), distinct from the diff.
- Chain verification: recompute every `row_hash`, confirm every `prev_hash` links, report any break.
- Assemble the seven sections from the spec, including the redress trail fields (appeal route, where signposting was added).
- Export: bundle the rows, the policy versions referenced, and an export hash.

**Definition of done.**
- For a case with author, edit, approve rows, the pack renders all seven sections from real rows.
- The draft-to-final diff is derived from the author and final payloads, not stored separately.
- Chain verification passes on an intact chain and fails loudly on a tampered one, both tested.
- The export hash is stable for the same input.
- **Wire the frontend:** the Proof pack renders from real LedgerRows, all seven sections, with the live chain-verification result at the top. This is the screen that proves the product, so make it real early.

---

## Sprint 4: capability broker skeleton

Before any connector. Build connector access around the broker from the start, never around a weaker model you mean to retrofit.

**Goal.** Authority issues short-lived, scoped, revocable capabilities. Nothing can fetch from a connector without one.

**Decisions to make first.**
- A capability is bound to one connector, one case, one action, and expires in minutes. Locked.
- Where the revocation list lives and how it is checked on every use.

**Build tasks.**
- Capability object and issuance: on an allow Verdict, mint a capability with `resource_scope`, `connector`, `expires_at`, `issued_by_verdict`, `revocable`.
- Issuance and revocation are both LedgerRows.
- A guard in the fetch path that rejects any call without a valid, unexpired, unrevoked capability that does not exceed its issuing Verdict.
- A mock connector that only accepts a valid capability, so the broker is exercised before a real connector exists.

**Definition of done.**
- A fetch with a valid capability succeeds; a fetch with none, an expired one, a revoked one, or one exceeding its Verdict is rejected. All tested.
- Issuance and revocation both appear in the ledger.
- A test proves the broker cannot be bypassed: there is no code path to the mock connector that skips the capability check.

---

## Sprint 5: one real connector, permission mirror, masking

**Goal.** Connect one real source through the broker, apply the permission mirror, and mask at the boundary before any record reaches the agent.

**Decisions to make first.**
- The first connector. SharePoint via Microsoft Graph or a mock case system, whichever is faster to stand up. The interface matters more than the target.
- The field sensitivity map for the housing example.

**Build tasks.**
- The connector interface and one adapter, fetching only under a brokered capability.
- The permission mirror: per field, outcome is the intersection of the source's own permission and the sensitivity policy. Allow, mask, or deny.
- Apply masking before the record leaves the boundary, and record `field_effects` on the LedgerRow.

**Definition of done.**
- A fetch returns a record with National Insurance masked and medical notes denied, matching the masking screen.
- The `field_effects` are on the ledger row, so an auditor can prove the data was never exposed. Tested.
- No fetch path exists that skips the permission mirror.

---

## Sprint 6: Arena scenario runner and Certificate

**Goal.** Run an agent against a scenario suite in shadow, score on safety, usefulness, and proof, and on success issue a Certificate backed by an AssuranceCase. This is where Intelligence stops being a stub.

**Build tasks.**
- Scenario object and a small suite for the housing example, including the deliberate adverse-send scenario that the agent must fail (a refusal is a pass).
- The runner: execute each scenario in shadow against the agent through the governed path.
- Three-axis scoring: safety as a binary gate on severe and catastrophic scenarios, usefulness informing the recommended ceiling, proof checking the audit trail is complete.
- On success: build the AssuranceCase (claims and evidence) and issue a Certificate carrying `assurance_case_ref`, a named sign-off, and an expiry. The builder cannot be the approver.

**Definition of done.**
- A passing agent earns a Certificate and an AssuranceCase with claims linked to scenario evidence.
- An agent that sends an adverse decision in the forbidden scenario fails certification, tested.
- The certificate lists certified and not-certified actions, matching the certificate screen.
- **Wire the frontend:** the Check agent, Test results, and Certificate screens render real Arena output and a real Certificate with its AssuranceCase. Intelligence is now live, so these stop being canned.

---

## Sprint 7: go-live gate and service-owner sign-off

**Goal.** Enforce the certificate at promotion, and require a human service owner to approve, with conditions.

**Build tasks.**
- The gate reads the certificate, confirms policy bound, source readiness above threshold, connectors healthy, and the human-judgement rule active. It enforces the certificate, it does not re-decide.
- A SourceReadinessSnapshot object the gate reads (score, threshold, flags, what it is blocked for).
- Service-owner sign-off: a deployment approval with approver, scope, trial period, review checkpoint, and conditions. A valid certificate alone does not equal deployment.

**Definition of done.**
- Promotion is allowed only up to the certified ceiling and only with a service-owner approval recorded.
- Sources below threshold block citizen-facing promotion, tested.
- The deployment approval is a ledger event.
- **Wire the frontend:** the Go-live check renders real certificate, readiness, and connector status, and the service-owner sign-off is a real write action (the first write the gate performs). The Sources screen renders the real SourceReadinessSnapshot.

---

## Sprint 8: Policy Workbench, propose and approve

**Goal.** Turn one real rule change into a new policy version through a human-approved pipeline. Not automatic law-to-code.

**Build tasks.**
- Source fetch for one real change (the section 21 abolition is the worked example), storing source URI, version, and content hash.
- Candidate extraction that proposes rules, shown to a human as a diff. No rule goes live unreviewed.
- Compilation of approved rules into a new Policy version with guards carrying their source provenance.
- Shadow replay against a set of historical or synthetic cases, producing the "what would change" report.
- Conflict handling: if sources disagree, hold and route to the policy owner.
- Activation on a future effective date, with certificate impact and in-flight transition rule, writing a policy-change ledger event.

**Definition of done.**
- A staged change can be previewed by shadow replay before its effective date.
- Activation flags impacted certificates and applies the transition rule, all ledgered.
- A decision before the change still audits under the old version forever, tested.
- A conflict between two sources holds rather than silently choosing, tested.
- **Wire the frontend:** the Policy versions screen shows a real staged change with a real shadow-replay preview, and the Activate action is a live write that runs the supersession. The Policy Workbench is the control surface behind it.

---

## Sprint 9: incident response and revocation

**Goal.** A live agent that misbehaves has a fast, ledgered containment path.

**Build tasks.**
- Triggers: missed escalation, guard breach, source gone stale, confidence collapse, human report.
- Automatic containment, fail closed: suspend the certificate, hold in-flight cases, disable the affected action class. Each is a ledger event, and suspending revokes the agent's live capabilities through the broker.
- Notify the service owner, require human review, force re-certification before return to live.
- An incident produces its own audit pack.

**Definition of done.**
- Triggering an incident suspends the certificate and the broker stops honouring that agent's capabilities immediately, tested.
- In-flight cases are held, not silently re-decided.
- The agent cannot return to live without re-certification, tested.
- **Wire the frontend:** the Head of department oversight and Per-agent log screens render real run-filtered LedgerRows and live metrics, and the restrict-action and suspend controls become real writes. This lights up the last screens; the whole demo now runs on the backend.

---

## Cross-cutting, true in every sprint

- Fail closed. Any unknown, missing, expired, or errored input resolves to hold or block.
- Thread `run_id` through every object and every ledger row.
- Idempotency on anything that writes or acts, so retries are safe.
- Every state-changing command is a ledger event, including control commands and approvals.
- The agent never calls Intelligence directly. Authority is always the first stop.
- Write the test in the same sprint as the feature.
- Wire one screen as each sprint makes it renderable. Read-only first, write actions only when the command behind them exists. Never rebuild a screen; replace its hardcoded body with fetched data.

---

## What this plan deliberately leaves for later

Multi-tenancy and isolation between departments, full identity and role management, the readiness scanner's deep inspection logic, production hardening of the broker and the Workbench, and operations (monitoring, backup, the external anchor's independent infrastructure). These are real and named in section 12 of the backend spec. They are not part of the first build, and trying to do them now would stall the spine. Build the spine first, deploy it for one workflow in shadow mode, and let the real deployment surface what to harden next.

---

## The first thing to do today

Sprint 0, then Sprint 1. The ledger is the one piece you cannot retrofit, so the first real milestone is a DecisionRequest in, a Verdict out, and a hash-chained row that a thousand concurrent writes cannot break. When that test is green, you have stopped designing and started building, and everything else in this plan hangs off it.
