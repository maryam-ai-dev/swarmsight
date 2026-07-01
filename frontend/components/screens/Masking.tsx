"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { useCase } from "@/lib/caseref";

// The seeded example case whose full un-masked record we can show side-by-side.
// For any other case the officer opens the full record in SharePoint directly.
const SEEDED_CASE = "HX-5821";

interface FieldEffect {
  field: string;
  sourcePermission: "ALLOW" | "MASK" | "DENY";
  policy: "ALLOW" | "MASK" | "DENY";
  outcome: "ALLOW" | "MASK" | "DENY";
}
interface SourceDoc {
  verdict: string;
  record: {
    connector: string;
    resourceScope: string;
    fields: Record<string, unknown>;
    fieldEffects: FieldEffect[];
    document: { id: string; version: string; name: string } | null;
  } | null;
}

const LABELS: Record<string, string> = {
  applicant_name: "Applicant name",
  income: "Income",
  tenancy_status: "Tenancy",
  national_insurance: "National Insurance",
  medical_notes: "Medical notes",
  internal_ref: "Caseworker reference",
};
const ORDER = [
  "applicant_name", "income", "tenancy_status",
  "national_insurance", "medical_notes", "internal_ref",
];

function display(field: string, value: unknown): string {
  if (field === "income" && value != null) return "£" + Number(value).toLocaleString();
  return String(value);
}

interface CaseDraft {
  verdict: string;
  proposedAction: string | null;
  rationale: string | null;
  draft: string | null;
  rejectionReason: string | null;
}

export function Masking({ active }: { active: ScreenId }) {
  const { caseRef } = useCase();
  const [doc, setDoc] = useState<SourceDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<CaseDraft | null>(null);
  const [drafting, setDrafting] = useState(false);

  // Closes the loop: the agent drafts from the masked record the broker fetched.
  async function runDraft() {
    setDrafting(true);
    setDraft(null);
    try {
      const r = await fetch(
        `/_authority/cases/${caseRef}/agent/draft`,
        { method: "POST" },
      );
      if (r.status === 401) {
        window.location.href = "/login";
        return;
      }
      setDraft((await r.json()) as CaseDraft);
    } catch (e) {
      setDraft({
        verdict: "",
        proposedAction: null,
        rationale: null,
        draft: null,
        rejectionReason: "Could not reach Authority (" + (e as Error).message + ").",
      });
    } finally {
      setDrafting(false);
    }
  }

  // Broker the fetch live when the screen opens: Authority decides, mints a
  // short-lived capability, the SharePoint connector fetches, the mirror masks.
  useEffect(() => {
    fetch(`/_authority/cases/${caseRef}/source-documents/fetch`, {
      method: "POST",
    })
      .then((r) => {
        if (r.status === 401) {
          window.location.href = "/login";
          return null;
        }
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      })
      .then((d) => d && setDoc(d as SourceDoc))
      .catch((e) => setError(e.message));
  }, [caseRef]);

  const effects = doc?.record?.fieldEffects ?? [];
  const byField = new Map(effects.map((e) => [e.field, e]));
  const fields = doc?.record?.fields ?? {};
  const orderedFields = [
    ...ORDER.filter((f) => byField.has(f)),
    ...effects.map((e) => e.field).filter((f) => !ORDER.includes(f)),
  ];

  return (
    <Screen id="masking" active={active} brand="reg-mask">
      <div className="wrap">
        <div className="kick">
          Housing application {caseRef} · SharePoint (Housing site)
        </div>
        <h1>What the agent is allowed to see</h1>
        <p className="lead">
          The application is fetched from the department&apos;s SharePoint through
          the capability broker, and masked at the boundary before it reaches the
          agent. This is the real record the agent received, not an illustration.
        </p>
        {doc?.record?.document && (
          <div className="note" style={{ marginTop: 8 }}>
            <b>Source document:</b> {doc.record.document.name} · version{" "}
            <span style={{ fontFamily: "ui-monospace, monospace" }}>
              {doc.record.document.version}
            </span>{" "}
            · {doc.record.connector}. Recorded on a <code>source_fetch</code> ledger
            row, so this case traces to the exact document and version it was built
            from; a changed version produces a new provenance entry.
          </div>
        )}
        <div className="two">
          <div className="col officer">
            <div className="colh">The officer sees (full record in SharePoint)</div>
            {caseRef === SEEDED_CASE ? (
              <>
                <div className="row">
                  <span className="f">Applicant name</span>
                  <span className="v">Ms A. Adeyemi</span>
                </div>
                <div className="row">
                  <span className="f">Income</span>
                  <span className="v">£18,400</span>
                </div>
                <div className="row">
                  <span className="f">Tenancy</span>
                  <span className="v">Confirmed</span>
                </div>
                <div className="row">
                  <span className="f">National Insurance</span>
                  <span className="v">QQ 12 34 56 C</span>
                </div>
                <div className="row">
                  <span className="f">Medical notes</span>
                  <span className="v">Disability, mobility</span>
                </div>
                <div className="row">
                  <span className="f">Caseworker reference</span>
                  <span className="v">HW-INT-2025-5821</span>
                </div>
              </>
            ) : (
              <div className="row">
                <span className="f">Full record</span>
                <span className="v">
                  Held in SharePoint. The officer opens {caseRef} directly; the
                  agent only ever receives the masked view on the right.
                </span>
              </div>
            )}
          </div>
          <div className="col agent">
            <div className="colh">The agent receives (live, from the broker)</div>
            {error && (
              <div className="row">
                <span className="f">Could not reach Authority</span>
                <span className="v">{error}</span>
              </div>
            )}
            {!doc && !error && (
              <div className="row">
                <span className="f">Fetching through the broker…</span>
                <span className="v" />
              </div>
            )}
            {doc && doc.record === null && (
              <div className="row">
                <span className="f">No access</span>
                <span className="v">
                  Verdict {doc.verdict}: the broker minted no capability.
                </span>
              </div>
            )}
            {orderedFields.map((f) => {
              const e = byField.get(f)!;
              const label = LABELS[f] || f;
              return (
                <div className="row" key={f}>
                  <span className="f">{label}</span>
                  <span className="v">
                    {e.outcome === "ALLOW" && display(f, fields[f])}
                    {e.outcome === "MASK" && (
                      <>
                        <span className="redact">XXXXXXXXX</span>{" "}
                        <span className="pill p-a">masked</span>
                      </>
                    )}
                    {e.outcome === "DENY" && <span className="pill p-d">no access</span>}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
        <div className="note">
          <b>Masked</b> means the agent knows a field exists but not its value.{" "}
          <b>No access</b> means the field is removed entirely, the agent never
          knows it was there. Every outcome below is the live result of the broker
          fetch.
        </div>
        {doc?.record && (
          <div className="rule">
            <b>How each field was decided</b> (SharePoint permission ∩ department
            policy = outcome):
            {effects.map((e) => (
              <div key={e.field} style={{ marginTop: 6 }}>
                {LABELS[e.field] || e.field}: {e.sourcePermission} ∩ {e.policy} ={" "}
                <b>{e.outcome}</b>
              </div>
            ))}
          </div>
        )}
        <div className="rule">
          <b>The agent drafting from this record.</b> The masked record above is
          the agent&apos;s entire view of the case. It never received the National
          Insurance number or medical notes, so they cannot appear in its draft.
          <div style={{ marginTop: 10 }}>
            <span
              className="lk"
              role="button"
              style={{ marginTop: 0 }}
              onClick={drafting ? undefined : runDraft}
            >
              {drafting ? "Drafting…" : "Have the agent draft from this record →"}
            </span>
          </div>
          {draft && draft.draft && (
            <div
              style={{
                marginTop: 12,
                border: "2px solid #0b0c0c",
                padding: "12px 14px",
                background: "#fff",
                fontSize: 15,
                lineHeight: 1.6,
              }}
            >
              {draft.draft}
            </div>
          )}
          {draft && !draft.draft && (
            <div style={{ marginTop: 10, color: "#6f6f69" }}>
              {draft.rejectionReason ||
                `Verdict ${draft.verdict}: ${draft.proposedAction || "no draft produced"}.`}
            </div>
          )}
          {draft && draft.draft && (
            <div style={{ marginTop: 8, fontSize: 13, color: "#6f6f69" }}>
              Proposed action: <b>{draft.proposedAction}</b> · produced from the
              masked record, through the broker. No National Insurance or medical
              data is present, because the agent never received it.
            </div>
          )}
        </div>
        <div className="rule">
          <b>How it is decided:</b> a field reaches the agent only if SharePoint&apos;s
          own permissions allow it <b>and</b> the department&apos;s sensitivity
          policy permits it at the agent&apos;s clearance. Both must agree; either
          can restrict. Every mask and denial is recorded on a{" "}
          <code>source_fetch</code> ledger row (the outcomes, never the values),
          so an auditor can prove the data was never exposed.
        </div>
        <NavLink to="case" className="lk">
          ← Back to case
        </NavLink>
      </div>
    </Screen>
  );
}
