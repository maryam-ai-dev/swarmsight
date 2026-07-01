"use client";

import { useCallback, useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import {
  authority,
  IngestionResult,
  PolicyChange,
  PolicyChangeView,
  PolicyVersion,
  ukDate,
} from "@/lib/api";
import { useUser } from "@/lib/user";
import { humanize } from "@/lib/labels";

interface ActivateResponse {
  result?: { newVersion: string; impactedCertificates: unknown[] };
  rejectionReason?: string;
}

function StatusPill({ status }: { status: string }) {
  if (status === "PROPOSED") return <span className="tag t-y">staged</span>;
  if (status === "HELD")
    return (
      <span className="tag" style={{ background: "#d4351c", color: "#fff" }}>
        held
      </span>
    );
  return <span className="tag t-g">activated</span>;
}

// The policy "calendar": each version is in force now, scheduled for a future
// date, or superseded by a later one. Derived from the immutable effective dates.
function VersionStatusPill({ status }: { status?: string }) {
  if (status === "ACTIVE") return <span className="tag t-b">in force</span>;
  if (status === "SCHEDULED") return <span className="tag t-y">scheduled</span>;
  if (status === "SUPERSEDED")
    return (
      <span className="tag" style={{ background: "#b1b4b6", color: "#0b0c0c" }}>
        superseded
      </span>
    );
  return null;
}

// One version of the rulebook: what the assistant may do, and the rules that
// send a case to a person. Reused across the in-force / scheduled / past groups.
function VersionCard({ v }: { v: PolicyVersion }) {
  const guards = v.guards || [];
  return (
    <div className="sl" style={{ marginBottom: 16 }}>
      <div className="r">
        <span className="k">
          Version {v.version} <VersionStatusPill status={v.status} />
        </span>
        <span className="vv">effective {ukDate(v.effectiveFrom)}</span>
      </div>
      <div style={{ marginTop: 10 }}>
        <b>The assistant may:</b>{" "}
        {(v.actions || []).map((a) => humanize(a)).join(", ") || "None"}
      </div>
      <div className="h2" style={{ marginTop: 12 }}>
        Rules that send a case to a person
      </div>
      {guards.length ? (
        guards.map((g, gi) => (
          <div className="src" key={gi}>
            <span className="l">{humanize(g.name)}</span> → {humanize(g.raiseTo)}{" "}
            · <span style={{ color: "#505a5f" }}>{g.source}</span>
          </div>
        ))
      ) : (
        <div className="src" style={{ color: "#505a5f" }}>
          No extra rules, standard handling.
        </div>
      )}
    </div>
  );
}

// Live policy ingestion: point Authority at published legislation; it fetches,
// hashes it, has Claude propose the guards and commencement date, and stages a
// change below for review. Never activates, the human gate still applies.
function IngestBox({
  policyId,
  onIngested,
}: {
  policyId: string;
  onIngested: () => void;
}) {
  const [url, setUrl] = useState(
    "https://www.legislation.gov.uk/ukpga/1988/50/section/21",
  );
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<IngestionResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function ingest() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const r = await authority.post<IngestionResult>(
        "/policy-ingestion/fetch",
        { url, policyId },
      );
      setResult(r);
      onIngested();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="sl" style={{ marginBottom: 16 }}>
      <div className="r">
        <span className="k">
          Ingest from a published source{" "}
          <span className="tag t-y">live</span>
        </span>
        <span className="vv">Microsoft Graph not required</span>
      </div>
      <p style={{ marginTop: 8, color: "#505a5f", fontSize: 14 }}>
        Authority fetches the document (HTML or PDF) from an allowlisted host,
        stores a hash of it for provenance, and Claude proposes the guards it
        implies and the date it comes into force. It is staged for review below;
        nothing goes live until a service owner activates it.
      </p>
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          style={{
            flex: 1,
            padding: "8px 10px",
            border: "2px solid #0b0c0c",
            fontSize: 14,
          }}
          placeholder="https://www.legislation.gov.uk/…"
        />
        <span
          className="btn"
          role="button"
          onClick={busy ? undefined : ingest}
        >
          {busy ? "Ingesting…" : "Fetch & extract"}
        </span>
      </div>
      {error && (
        <div className="diffnote" style={{ marginTop: 10 }}>
          Could not ingest: {error}
        </div>
      )}
      {result && (
        <div style={{ marginTop: 12 }}>
          <div>
            <b>{result.summary || result.title}</b>
          </div>
          <div className="src">
            <span className="l">Source</span> {result.sourceUri}
          </div>
          <div className="src">
            <span className="l">Provenance hash</span>{" "}
            {result.contentHash.slice(0, 16)}… ({result.fetchedChars} bytes read)
          </div>
          <div className="src">
            <span className="l">Extraction</span>{" "}
            {result.extraction === "claude"
              ? "Claude read the document"
              : "offline worked example"}{" "}
            · {result.addedGuards} guard(s) proposed
          </div>
          <div className="src">
            <span className="l">Comes into force</span>{" "}
            {result.commencementDate
              ? ukDate(result.commencementDate)
              : "unstated, suggesting " +
                ukDate(result.suggestedEffectiveFrom)}
          </div>
          <div style={{ marginTop: 8, color: "#00703c", fontWeight: 600 }}>
            Staged as {result.proposedVersion}, review the diff and shadow
            replay below, then activate.
          </div>
        </div>
      )}
    </div>
  );
}

// Infer a policy from a document the department already holds in SharePoint (its
// own written policy). Lists the policy documents in the library and stages a
// proposed change from the chosen one. Same human gate as everything else.
function SharePointPolicyBox({
  policyId,
  onIngested,
}: {
  policyId: string;
  onIngested: () => void;
}) {
  const [docs, setDocs] = useState<{ name: string; version: string }[] | null>(
    null,
  );
  const [chosen, setChosen] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<IngestionResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authority
      .get<{ name: string; version: string }[]>("/policy-documents")
      .then((d) => {
        setDocs(d);
        if (d.length) setChosen(d[0].name);
      })
      .catch((e) => setError((e as Error).message));
  }, []);

  async function infer() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const r = await authority.post<IngestionResult>(
        "/policy-documents/infer",
        { document: chosen, policyId },
      );
      setResult(r);
      onIngested();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="sl" style={{ marginBottom: 16 }}>
      <div className="r">
        <span className="k">
          Infer from your SharePoint policy document{" "}
          <span className="tag t-y">live</span>
        </span>
        <span className="vv">Microsoft Graph</span>
      </div>
      <p style={{ marginTop: 8, color: "#505a5f", fontSize: 14 }}>
        Authority reads a policy document the department holds in its own
        SharePoint library, stores a hash of it for provenance, and Claude
        proposes the guards it implies and the date it comes into force. Staged
        for review below; nothing goes live until a service owner activates it.
      </p>
      {docs && docs.length === 0 && (
        <div className="diffnote" style={{ marginTop: 10 }}>
          No policy documents found in the library. Drop a file whose name
          contains &ldquo;policy&rdquo; (e.g.{" "}
          <code>Policy-Homelessness-Triage-HL-01.txt</code>) into the SharePoint
          site and reload.
        </div>
      )}
      {docs && docs.length > 0 && (
        <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
          <select
            value={chosen}
            onChange={(e) => setChosen(e.target.value)}
            style={{
              flex: 1,
              minWidth: 240,
              padding: "8px 10px",
              border: "2px solid #0b0c0c",
              fontSize: 14,
              fontFamily: "inherit",
            }}
          >
            {docs.map((d) => (
              <option key={d.name} value={d.name}>
                {d.name}
              </option>
            ))}
          </select>
          <span className="btn" role="button" onClick={busy ? undefined : infer}>
            {busy ? "Reading…" : `Infer ${policyId} guards`}
          </span>
        </div>
      )}
      {error && (
        <div className="diffnote" style={{ marginTop: 10 }}>
          Could not infer: {error}
        </div>
      )}
      {result && (
        <div style={{ marginTop: 12 }}>
          <div>
            <b>{result.summary || result.title}</b>
          </div>
          <div className="src">
            <span className="l">Source</span> {result.sourceUri}
          </div>
          <div className="src">
            <span className="l">Provenance hash</span>{" "}
            {result.contentHash.slice(0, 16)}… ({result.fetchedChars} bytes read)
          </div>
          <div className="src">
            <span className="l">Extraction</span>{" "}
            {result.extraction === "claude"
              ? "Claude read the document"
              : "offline worked example"}{" "}
            · {result.addedGuards} guard(s) proposed
          </div>
          <div className="src">
            <span className="l">Comes into force</span>{" "}
            {result.commencementDate
              ? ukDate(result.commencementDate)
              : "unstated, suggesting " +
                ukDate(result.suggestedEffectiveFrom)}
          </div>
          <div style={{ marginTop: 8, color: "#00703c", fontWeight: 600 }}>
            Staged as {result.proposedVersion}, review the diff and shadow
            replay below, then activate.
          </div>
        </div>
      )}
    </div>
  );
}

function StagedChange({
  view,
  onActivated,
}: {
  view: PolicyChangeView;
  onActivated: () => void;
}) {
  const c = view.change;
  const rep = view.shadowReport;
  const [status, setStatus] = useState("");

  // Pre-fill the effective date with the date the change suggests (an ingested
  // commencement date), else 90 days out. The service owner can override it.
  const defaultDate = (
    c.suggestedEffectiveFrom
      ? c.suggestedEffectiveFrom.slice(0, 10)
      : new Date(Date.now() + 90 * 864e5).toISOString().slice(0, 10)
  );
  const [effDate, setEffDate] = useState(defaultDate);

  async function activate() {
    setStatus("Running the supersession…");
    try {
      const o = await authority.post<ActivateResponse>(
        `/policy-changes/${encodeURIComponent(c.id)}/activate`,
        {
          approver: "Policy Owner, Housing",
          effectiveFrom: effDate + "T00:00:00Z",
        },
      );
      if (o.result) {
        setStatus(
          `Activated as ${o.result.newVersion}, effective ${ukDate(effDate)}. ${o.result.impactedCertificates.length} certificate(s) flagged for review. Ledgered.`,
        );
        onActivated();
      } else {
        setStatus("Activation refused: " + (o.rejectionReason || "blocked"));
      }
    } catch (err) {
      setStatus("Activation failed (" + (err as Error).message + ").");
    }
  }

  const addedGuards = view.diff.addedGuards || [];

  return (
    <div className="sl" style={{ marginBottom: 16 }}>
      <div className="r">
        <span className="k">
          {c.policyId} {c.baseVersion} to {c.proposedVersion}{" "}
          <StatusPill status={c.status} />
        </span>
        <span className="vv">{c.sources[0]?.title || ""}</span>
      </div>
      <div className="h2" style={{ marginTop: 10 }}>
        New rules this adds
      </div>
      {addedGuards.length ? (
        addedGuards.map((g, i) => (
          <div className="src" key={i}>
            <span className="l">+ {humanize(g.name)}</span> → {humanize(g.raiseTo)}{" "}
            <span style={{ color: "#505a5f" }}>({g.source})</span>
          </div>
        ))
      ) : (
        <div className="src" style={{ color: "#505a5f" }}>
          No new rules.
        </div>
      )}
      {rep && (
        <>
          <div style={{ marginTop: 10 }}>
            <b>Tested against real cases:</b> {rep.changedCount} of{" "}
            {rep.totalCount} would be decided differently.
          </div>
          {(rep.results || [])
            .filter((x) => x.changed)
            .map((x, i) => (
              <div className="src" key={i}>
                <span>
                  {humanize(x.caseRef)}: {humanize(x.oldEffect)} becomes{" "}
                  {humanize(x.newEffect)}
                </span>
                <span className="pill p-missing" style={{ marginLeft: 10 }}>
                  would change
                </span>
              </div>
            ))}
        </>
      )}
      {c.status === "PROPOSED" && (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 8, fontSize: 14, color: "#505a5f" }}>
            Effective from{" "}
            <input
              type="date"
              value={effDate}
              onChange={(e) => setEffDate(e.target.value)}
              style={{
                padding: "4px 8px",
                border: "2px solid #0b0c0c",
                fontSize: 14,
              }}
            />
            {c.suggestedEffectiveFrom && (
              <span style={{ marginLeft: 8 }}>
                · suggested from the source: {ukDate(c.suggestedEffectiveFrom)}
              </span>
            )}
          </div>
          <span className="btn" onClick={activate} role="button">
            Activate on {ukDate(effDate)}
          </span>
          <span
            style={{ marginLeft: 12, fontSize: 14, color: "#505a5f" }}
          >
            {status}
          </span>
        </div>
      )}
      {c.status === "HELD" && (
        <div className="diffnote" style={{ marginTop: 10 }}>
          {c.conflictReason || ""}
        </div>
      )}
    </div>
  );
}

// The department (borough housing service) runs one rulebook per workflow.
const WORKFLOWS = [
  { id: "HA-09", name: "Housing appeals" },
  { id: "HL-01", name: "Homelessness" },
  { id: "RP-01", name: "Repairs" },
  { id: "FOI-01", name: "FOI redaction" },
];

export function Policies({ active }: { active: ScreenId }) {
  const { user } = useUser();
  const canIngest =
    user?.role === "SERVICE_OWNER" || user?.role === "ADMIN";
  const [policyId, setPolicyId] = useState("HA-09");
  const [versions, setVersions] = useState<PolicyVersion[] | null>(null);
  const [versionsError, setVersionsError] = useState<string | null>(null);
  const [staged, setStaged] = useState<PolicyChangeView[] | null>(null);
  const [stagedError, setStagedError] = useState<string | null>(null);

  const loadVersions = useCallback(() => {
    setVersions(null);
    setVersionsError(null);
    authority
      .get<PolicyVersion[]>(`/policies/${encodeURIComponent(policyId)}/versions`)
      .then(setVersions)
      .catch((err) => setVersionsError(err.message));
  }, [policyId]);

  const loadStaged = useCallback(() => {
    setStaged(null);
    authority
      .get<PolicyChange[]>(
        `/policy-changes?policyId=${encodeURIComponent(policyId)}`,
      )
      .then((changes) =>
        Promise.all(
          changes.map((c) =>
            authority.get<PolicyChangeView>(
              `/policy-changes/${encodeURIComponent(c.id)}`,
            ),
          ),
        ),
      )
      .then(setStaged)
      .catch((err) => setStagedError(err.message));
  }, [policyId]);

  useEffect(() => {
    loadVersions();
    loadStaged();
  }, [loadVersions, loadStaged]);

  return (
    <Screen id="policies" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Housing appeals</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>
          Policy is versioned data. A change is a new version, never an edit.
        </span>
      </div>
      <div className="mn">
        <div className="crumb">
          <NavLink to="case" className="lk">
            HX-4471
          </NavLink>{" "}
          › Policy versions
        </div>
        <h1>Kensington &amp; Chelsea housing policies</h1>
        <p className="lead">
          The department runs one rulebook per workflow: each decides which cases
          the assistant may prepare and which a person must decide. Pick a
          workflow to see its versions.
        </p>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "14px 0 4px" }}>
          {WORKFLOWS.map((w) => (
            <span
              key={w.id}
              role="button"
              onClick={() => setPolicyId(w.id)}
              className={"gov-btn" + (w.id === policyId ? "" : " g-grey")}
              style={{ fontSize: 14 }}
            >
              {w.name} ({w.id})
            </span>
          ))}
        </div>
        <div className="note" style={{ marginTop: 4 }}>
          This page is about the <b>rules</b>. What <b>documents</b> the assistant
          is allowed to see is a separate control,{" "}
          <NavLink to="masking" className="lk" style={{ marginTop: 0 }}>
            see Sensitive data
          </NavLink>
          . A change is a new version, never an edit, so every past decision can
          still be judged under the rules that applied at the time.
        </div>

        {versionsError ? (
          <div style={{ marginTop: 16 }}>
            Could not reach Authority ({versionsError}).
          </div>
        ) : !versions ? (
          <div style={{ marginTop: 16 }}>Loading the rulebook…</div>
        ) : (
          (() => {
            const current = versions.filter((v) => v.status === "ACTIVE");
            const scheduled = versions.filter((v) => v.status === "SCHEDULED");
            const past = versions.filter((v) => v.status === "SUPERSEDED");
            const group = (
              label: string,
              hint: string,
              items: PolicyVersion[],
            ) =>
              items.length > 0 && (
                <>
                  <div className="h2" style={{ marginTop: 22 }}>
                    {label} <span className="c">{hint}</span>
                  </div>
                  <div style={{ marginTop: 12 }}>
                    {items.map((v) => (
                      <VersionCard key={v.policyId + v.version} v={v} />
                    ))}
                  </div>
                </>
              );
            return (
              <>
                {group("In force now", "the rules being applied today", current)}
                {group(
                  "Scheduled",
                  "approved, takes effect on a future date",
                  scheduled,
                )}
                {group(
                  "Past versions",
                  "replaced, kept so old decisions stay auditable",
                  past,
                )}
              </>
            );
          })()
        )}

        <div className="h2" style={{ marginTop: 28 }}>
          Propose a change{" "}
          <span className="c">from your policy document or new legislation</span>
        </div>
        <p style={{ color: "#505a5f", fontSize: 14, margin: "6px 0 0" }}>
          A change is drafted, previewed against real cases, and only takes effect
          after a person approves it on a future date. Nothing here changes a live
          rule on its own.
        </p>
        {canIngest && (
          <div style={{ marginTop: 12 }}>
            <SharePointPolicyBox
              policyId={policyId}
              onIngested={() => {
                loadVersions();
                loadStaged();
              }}
            />
            <IngestBox
              policyId={policyId}
              onIngested={() => {
                loadVersions();
                loadStaged();
              }}
            />
          </div>
        )}
        <div style={{ marginTop: 12 }}>
          {stagedError ? (
            "Could not reach Authority (" + stagedError + ")."
          ) : !staged ? (
            "Loading proposed changes…"
          ) : staged.length === 0 ? (
            "No changes proposed."
          ) : (
            staged.map((v) => (
              <StagedChange
                key={v.change.id}
                view={v}
                onActivated={() => {
                  loadVersions();
                  loadStaged();
                }}
              />
            ))
          )}
        </div>
      </div>
    </Screen>
  );
}
