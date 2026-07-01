"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, CASE_REF, Verdict, shortHash } from "@/lib/api";
import { humanize } from "@/lib/labels";

const EFFECT_LABEL: Record<string, string> = {
  ALLOW: "Cleared",
  HOLD: "Held for an officer",
  BLOCK: "Blocked",
};

export function CaseScreen({ active }: { active: ScreenId }) {
  const [verdict, setVerdict] = useState<Verdict | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authority
      .get<Verdict>(`/cases/${encodeURIComponent(CASE_REF)}/verdict`)
      .then(setVerdict)
      .catch((err) => setError(err.message));
  }, []);

  const warn = verdict
    ? verdict.reviewBrief
    : "Involves eviction risk and dependent children. This decision needs an officer.";
  const lead = verdict
    ? verdict.reviewBrief
    : "For your review because the case mentions eviction risk and dependent children, and the evidence is incomplete, under Housing Appeals Policy HA-09, section 4.";
  const certline = verdict
    ? `Decided under Housing policy ${verdict.policyVersion}, ${humanize(verdict.reasonCode)}. The assistant is cleared to draft, not to send.`
    : "Prepared under certificate SWS-CERT-HX-V3-0047 · policy HA-09 v7. The service is certified to draft, not to send.";

  return (
    <Screen id="case" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Housing appeals</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>Supervised trial. You make the decision.</span>
      </div>
      <div className="mn">
        <div className="crumb">
          <NavLink to="officer" className="lk">
            Cases for review
          </NavLink>{" "}
          › HX-4471
        </div>
        <h1>HX-4471, housing appeal</h1>
        <div
          style={{
            marginTop: 10,
            fontSize: 13,
            color: verdict
              ? verdict.effect === "ALLOW"
                ? "#00703c"
                : "#d4351c"
              : "#505a5f",
          }}
        >
          {error ? (
            "Could not reach Authority (" + error + "). Showing demo text."
          ) : verdict ? (
            <>
              Live verdict from Authority:{" "}
              <b>{EFFECT_LABEL[verdict.effect] || verdict.effect}</b> · ledger
              row #{verdict.seq} · row hash{" "}
              <code>{shortHash(verdict.rowHash)}</code>
            </>
          ) : (
            "Loading verdict from Authority…"
          )}
        </div>
        <div className="warn" style={{ marginTop: 16 }}>
          <span className="ico">!</span>
          <span className="tx">{warn}</span>
        </div>
        <p className="lead">{lead}</p>
        <div className="h2">Evidence</div>
        <p className="src" style={{ marginTop: 14 }}>
          <span className="l">Income above threshold</span>, assessment.pdf p.3 ·{" "}
          <b>strong</b>
        </p>
        <p className="src">
          <span className="l">Current tenancy confirmed</span>, tenancy record ·{" "}
          <b>strong</b>
        </p>
        <p className="src">
          <span className="l">Latest eviction notice</span> · <b>missing</b>
        </p>
        <p className="src" style={{ color: "#505a5f" }}>
          <span className="l">Withheld from the AI:</span> medical notes,
          National Insurance number.
        </p>
        <div className="h2">
          Prepared response <span className="c">draft, you can edit</span>
        </div>
        <div className="draft">
          Dear Ms Adeyemi, thank you for your appeal regarding your housing
          application. Having reviewed your income and tenancy documents, I am
          writing to confirm the next steps in your case…
        </div>
        <div className="sl">
          <div className="r">
            <span className="k">Decision</span>
            <span className="vv">You</span>
          </div>
          <div className="r">
            <span className="k">The service did</span>
            <span className="vv">Prepared evidence and a draft</span>
          </div>
        </div>
        <div className="certline">{certline}</div>
        <div style={{ marginTop: 8 }}>
          <span className="btn">Accept and send</span>
          <span className="btn sec">Edit draft</span>
          <span className="btn sec">Request evidence</span>
          <span className="btn sec">Escalate to senior</span>
        </div>
        <div className="nav2">
          <NavLink to="officer" className="lk">
            ← Back to cases
          </NavLink>
          <span style={{ display: "flex", gap: 18 }}>
            <NavLink to="policies" className="lk">
              Policy versions
            </NavLink>
            <NavLink to="masking" className="lk">
              What the agent could see
            </NavLink>
            <NavLink to="audit" className="lk">
              See the full record →
            </NavLink>
          </span>
        </div>
      </div>
    </Screen>
  );
}
