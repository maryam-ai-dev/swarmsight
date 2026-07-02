# SwarmSight

**A governance layer that lets government safely put AI agents onto real
casework — by making the agent structurally unable to leak data, act on its own,
or break policy.**

Authority decides, Intelligence proposes, the ledger proves it. A human makes
every decision that affects a citizen; the agent only prepares and checks.

---

## The problem: public-sector work is slow because it *has to be* careful

A council can't just point an AI agent at housing casework. Before anything can
happen, a person has to redact sensitive data, check the case against current
policy, and — crucially — someone has to be able to *prove* afterwards that no
rule was broken and no data was leaked. That caution is correct, but it's what
makes the work take hours per case and makes adopting *any* new tool take months.

SwarmSight keeps the caution and removes the time. It turns four slow manual
gates into automatic, provable ones:

| What used to be slow | Before | With SwarmSight |
|---|---|---|
| **Redact sensitive data** before an agent/tool can touch a case | Manual, per case, error-prone | A capability broker masks NI numbers, medical notes, etc. **at the boundary** — the agent never receives them. Instant, per fetch, ledgered. |
| **Assure an AI tool** is safe for a regulated workflow | Weeks of manual review + sign-off | The **Arena** generates test scenarios from the department's own policy, runs them against the live agent, and issues a certificate with a hard ceiling — in minutes. |
| **Turn a new policy or law into enforced rules** | Weeks of interpretation + rollout | Claude reads the policy document (or live UK legislation) and proposes the rules it implies + the date it takes effect; a human approves once; it's versioned. |
| **Prove to an auditor what happened** | Days of reconstruction | A hash-chained, append-only ledger records every decision, mask, and fetch. One-click proof pack, tamper-evident. |

The guarantee isn't "the agent behaved." It's "the agent *couldn't* misbehave,
and here's the proof."

---

## What it is

SwarmSight sits between untrusted AI agents and government systems (SharePoint,
case data). Every agent is governed by four mechanisms:

- **Capability broker** — the agent never touches source systems directly. It
  asks the broker, which fetches from SharePoint and masks sensitive fields
  *before* anything reaches the agent. Masking is a per-field intersection of the
  source's own permission and the department's sensitivity policy.
- **Department-owned, versioned policy** — agents don't own their rules. The
  *department* holds a versioned, append-only rulebook per workflow; an agent is
  matched to the policy that governs its task. Policies are inferred from the
  council's documents and UK legislation, never hardcoded. A change is a new
  version, so past decisions stay auditable under the rules that applied at the time.
- **The Arena** — before an agent goes live it's tested against scenarios
  generated from the actual policy (one per rule, plus adversarial cases) and
  earns a certificate with a hard ceiling ("prepare and check only" — never send,
  close, or release).
- **Append-only, hash-chained ledger** — every decision, mask, and fetch is
  recorded and verifiable, so any action can be proven after the fact.

---

## Architecture

```
┌────────────┐   proxied    ┌───────────────────────────┐      ┌──────────────┐
│ frontend   │  /_authority │ authority (Java 21)        │      │  SharePoint  │
│ Next.js    │─────────────▶│  broker · policy · arena   │─────▶│  (MS Graph)  │
│ (browser)  │              │  ledger · auth             │      └──────────────┘
└────────────┘              │                            │      ┌──────────────┐
                            │        decides, masks      │─────▶│  Postgres 16 │
                            └─────────────┬──────────────┘      └──────────────┘
                                          │ governs (agent only proposes)
                                          ▼
                            ┌───────────────────────────┐
                            │ intelligence (FastAPI)     │
                            │  POST /agent/act — Claude  │
                            └───────────────────────────┘
```

- **`authority/`** — Spring Boot (Java 21), Postgres 16. Holds the ledger, the
  decisions, the capability broker, the policy engine, the arena, and auth. This
  is where verdicts are made and proven. **The agent never calls Intelligence
  directly — Authority is always the first stop.**
- **`intelligence/`** — FastAPI (Python 3.12). The live agent under assurance:
  `POST /agent/act` returns a *proposed* action. It reasons with Claude
  (`claude-opus-4-8`) when `ANTHROPIC_API_KEY` is set, and falls back to a
  deterministic safe agent otherwise, so the stack runs offline. It only
  proposes; Authority decides, the certificate constrains, the broker masks.
- **`frontend/`** — Next.js (React). Role-based desks (officer / head of
  department / service owner), a guided story tour, and a live control tower.
  The browser only ever talks to this origin; it proxies API calls to Authority
  server-side (no CORS, no token in the browser).

---

## Running locally

```
docker compose up --build
```

Brings up Authority, Intelligence, and Postgres. Authority runs Flyway
migrations and seeds the demo on first boot.

- Authority health: http://localhost:8080/health
- Intelligence health: http://localhost:8000/health

Then start the frontend (it runs as a host process, not in compose):

```
cd frontend && npm install && npm run dev
```

Open **http://localhost:3000** and sign in.

### Demo accounts

Seeded on first boot when `swarmsight.demo-seed` is on (the default). With the
`docker-compose.yml` dev values:

| Account | Email | Password | Sees |
|---|---|---|---|
| Officer | `officer@swarmsight.local` | `swarmsight-demo` | The live caseload |
| Head of dept | `head@swarmsight.local` | `swarmsight-demo` | Oversight, containment |
| Service owner | `owner@swarmsight.local` | `swarmsight-demo` | Policy inference, agent assurance |
| Admin | `admin@swarmsight.local` | `changeme-admin` | Account management |

These are **dev-only** values set in `docker-compose.yml` (`AUTH_*`). Override
every one in production: `AUTH_JWT_SECRET` (>= 32 bytes), `AUTH_ADMIN_PASSWORD`,
and set `swarmsight.demo-seed` off so no demo accounts are seeded.

---

## Connecting live SharePoint (optional)

The `sharepoint-housing` connector reads live documents over Microsoft Graph
when configured, and falls back to an in-process mock otherwise — so the whole
demo runs with **no tenant**. To go live, register an Entra app (client secret,
Graph `Sites.Selected` or `Sites.Read.All` with admin consent) and set:

```
SHAREPOINT_TENANT_ID=<directory (tenant) id>
SHAREPOINT_CLIENT_ID=<application (client) id>
SHAREPOINT_CLIENT_SECRET=<client secret value>
SHAREPOINT_SITE=contoso.sharepoint.com:/sites/Housing
SHAREPOINT_MODE=document
```

Drop application documents named with a case ref (e.g.
`Housing-Application-HX-5821.txt`) into the site's library and they appear as
live cases. The log line `SharePoint connector: mode=..., graph=live` confirms
it; `GET /sources/sharepoint/health` tests each Graph step and names any that fails.

Extraction (reading fields from a document) runs *before* the permission mirror,
so the agent still only ever sees the masked record (NI masked, medical/unmapped
denied) regardless of where the data came from.

---

## Trying the governed path directly

```
curl -X POST http://localhost:8080/decide -H 'Content-Type: application/json' -d '{
  "requestId": "demo-1", "runId": "run-1", "caseRef": "CASE-1",
  "actor": "agent-housing-1", "workflow": "HA-09", "action": "draft_response",
  "inputs": {"tenancy_status": "secure", "eviction_risk": true, "dependent_children": true}
}'
```

A case with eviction risk **and** dependent children holds for an officer with a
plain-English brief, rather than letting the agent proceed.

---

## Running tests

Authority (needs a running Docker daemon — integration tests use Testcontainers
for Postgres):

```
cd authority && mvn test
```

Intelligence:

```
cd intelligence && pip install -r requirements.txt && pytest
```

---

## Deploying

The demo deploys to **Railway** (Postgres + authority + intelligence) and
**Vercel** (frontend). The whole frontend→backend contract is one env var,
`AUTHORITY_ORIGIN`; the browser never makes a cross-origin call. See `DEPLOY.md`
for the step-by-step checklist.

## Tech

Java 21 · Spring Boot · PostgreSQL · Flyway · Python · FastAPI · Next.js /
React · Microsoft Graph (SharePoint) · Anthropic Claude (`claude-opus-4-8`).
