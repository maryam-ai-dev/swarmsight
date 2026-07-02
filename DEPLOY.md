# Deploying the SwarmSight demo

A demo deployment has three backend pieces on **Railway** and the frontend on
**Vercel**. The browser only ever talks to the Vercel origin: Next.js
`rewrites()` proxy `/_authority/*` to Authority server-side (see
`frontend/next.config.js`), so there is no CORS to configure and no backend
secret ever reaches the browser.

```
Vercel (frontend)  ──rewrites──▶  Authority  (public HTTPS)
   AUTHORITY_ORIGIN=…                 ├─▶ Postgres      (managed, private)
                                      └─▶ Intelligence  (public HTTPS, called by Authority)
```

The whole frontend→backend contract is one variable: **`AUTHORITY_ORIGIN`**.
The UI never calls Intelligence directly — only Authority does — but on Railway
we still give Intelligence a public domain (see the note in step 1b: Railway's
private network is IPv6-only and the container binds IPv4, so the public URL is
the reliable path for a demo).

This is a **demo** setup. The auth defaults below are deliberately weak; rotate
every secret (and prefer SharePoint `Sites.Selected`) before anything real.

---

## 1. Backend on Railway

Create a project at <https://railway.app> (New Project → Deploy from GitHub repo,
pointed at this repo). Add three services to the same project.

### 1a. Postgres

New → **Database → Add PostgreSQL**. Railway provisions it and exposes
`PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` as referenceable variables.

### 1b. Intelligence service

New → **GitHub Repo** → this repo → **Settings → Root Directory: `intelligence`**
(it builds `intelligence/Dockerfile`, listens on `8000`).

Variables:

| Key                 | Value                          |
| ------------------- | ------------------------------ |
| `ANTHROPIC_API_KEY` | your Anthropic key             |
| `ANTHROPIC_MODEL`   | `claude-opus-4-8` (optional)   |

**Settings → Networking → Generate Domain.** Copy it, e.g.
`https://intelligence-production-xxxx.up.railway.app` — Authority uses this as
`INTELLIGENCE_BASE_URL` in the next step.

> Why public and not the private network: Railway's internal DNS is IPv6-only,
> but the container runs `uvicorn --host 0.0.0.0` (IPv4), so Authority can't
> reach it privately without a Dockerfile change. The public HTTPS domain is the
> zero-friction path for a demo. To keep it private instead, change the
> Intelligence `CMD` to bind `--host ::` and set
> `INTELLIGENCE_BASE_URL=http://${{intelligence.RAILWAY_PRIVATE_DOMAIN}}:8000`.

### 1c. Authority service (public)

New → **GitHub Repo** → this repo → **Settings → Root Directory: `authority`**
(builds `authority/Dockerfile`, listens on `8080`).

Variables (use Railway's `${{ ... }}` references so they stay in sync):

| Key                       | Value                                                              |
| ------------------------- | ----------------------------------------------------------------- |
| `AUTHORITY_DB_URL`        | `jdbc:postgresql://${{Postgres.PGHOST}}:5432/${{Postgres.PGDATABASE}}` |
| `AUTHORITY_DB_USER`       | `${{Postgres.PGUSER}}`                                             |
| `AUTHORITY_DB_PASSWORD`   | `${{Postgres.PGPASSWORD}}`                                         |
| `INTELLIGENCE_BASE_URL`   | the Intelligence public domain from step 1b (e.g. `https://intelligence-production-xxxx.up.railway.app`) |
| `ANTHROPIC_API_KEY`       | your Anthropic key                                                 |
| `AUTH_JWT_SECRET`         | any string of **32+ bytes**                                        |
| `AUTH_ADMIN_EMAIL`        | `admin@swarmsight.local`                                           |
| `AUTH_ADMIN_PASSWORD`     | pick one                                                           |
| `AUTH_DEMO_PASSWORD`      | `swarmsight-demo` (matches the demo persona logins)               |

SharePoint (optional — omit **all** of these to run the offline mock caseload;
the demo still works end-to-end):

| Key                        | Value                                            |
| -------------------------- | ------------------------------------------------ |
| `SHAREPOINT_TENANT_ID`     | your tenant id                                   |
| `SHAREPOINT_CLIENT_ID`     | app registration client id                       |
| `SHAREPOINT_CLIENT_SECRET` | app registration secret                          |
| `SHAREPOINT_SITE`          | e.g. `thirtylabsltd.sharepoint.com:/sites/Housing` |
| `SHAREPOINT_MODE`          | `document`                                        |

Then **Settings → Networking → Generate Domain**. Copy the result, e.g.
`https://authority-production-xxxx.up.railway.app`. That is your
`AUTHORITY_ORIGIN`.

> First boot runs Flyway migrations and the demo seed automatically (4 agents,
> policies HA-09/HL-01/RP-01/FOI-01, one certified assistant). Give it ~40s.
> Health check: `GET https://<authority-domain>/health` → `200`.

---

## 2. Frontend on Vercel

Import this repo at <https://vercel.com/new>.

- **Root Directory: `frontend`** (this is a monorepo — required).
- Framework preset: **Next.js** (auto-detected; `frontend/vercel.json` pins it).
- Environment Variable:

  | Key                | Value                                             |
  | ------------------ | ------------------------------------------------- |
  | `AUTHORITY_ORIGIN` | the Railway Authority domain from step 1c         |

- Deploy.

Your demo link is the Vercel URL. The session cookie is already `httpOnly` +
`secure`, and Vercel serves HTTPS, so login works with no extra config.

---

## 3. Demo logins

At `<vercel-url>/login`, password `swarmsight-demo` (or whatever you set as
`AUTH_DEMO_PASSWORD`):

| Email                        | Role              | Sees                                   |
| ---------------------------- | ----------------- | -------------------------------------- |
| `owner@swarmsight.local`     | Service owner     | Policy inference + ingestion controls  |
| `head@swarmsight.local`      | Head of dept      | Oversight, containment                 |
| `officer@swarmsight.local`   | Officer           | The live caseload                      |

---

## Redeploys

- Push to the repo → Railway and Vercel both auto-build from the branch.
- Reset the demo database: in Railway, restart the Authority service after
  wiping the Postgres volume (the seed re-runs on a fresh DB). The policy table
  is append-only, so this is how you clear test-staged policy versions.

## Notes / gotchas

- **`AUTHORITY_ORIGIN` must be HTTPS** — Vercel rewrites to it from its servers.
  Railway domains are HTTPS, so this is automatic.
- Intelligence has a public domain but the browser never calls it (only
  Authority does). It exposes just `/agent/act` and `/health`; for a short demo
  that is fine. To lock it down, make it private with the `--host ::` change in
  step 1b, or add Railway's access controls.
- No secrets live in this file or in the frontend bundle. `.env` is gitignored;
  set real values in the Railway/Vercel dashboards.
