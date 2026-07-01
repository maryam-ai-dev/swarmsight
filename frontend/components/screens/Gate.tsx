"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, GoLive } from "@/lib/api";
import { useAgent } from "@/lib/agent";
import { ceilingLabel, CEILING_EXPLAINER } from "@/lib/labels";

interface Approval {
  approver: string;
  grantedCeiling: string;
  trialPeriod: string;
}
interface ApprovalResponse {
  approval?: Approval;
  rejectionReason?: string;
}

export function Gate({ active }: { active: ScreenId }) {
  const { agentId } = useAgent();
  const [gate, setGate] = useState<GoLive | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [promoteStatus, setPromoteStatus] = useState("");
  const [promoted, setPromoted] = useState<Approval | null>(null);

  useEffect(() => {
    setGate(null);
    setError(null);
    authority
      .get<GoLive>(`/agents/${encodeURIComponent(agentId)}/go-live`)
      .then(setGate)
      .catch((err) => setError(err.message));
  }, [agentId]);

  // The sign-off is a real write, the first the gate performs.
  async function promote() {
    setPromoteStatus("Recording the service-owner sign-off…");
    try {
      const o = await authority.post<ApprovalResponse>(
        `/agents/${encodeURIComponent(agentId)}/deployment-approval`,
        {
          approver: "Head of Housing Service",
          scope: "Housing appeals, supervised live trial",
          trialPeriod: "8 weeks",
          reviewCheckpoint: "review at week 4",
          conditions: [
            "Officer signs every citizen decision",
            "Daily oversight review",
          ],
        },
      );
      if (o.approval) {
        setPromoteStatus("");
        setPromoted(o.approval);
      } else {
        setPromoteStatus(
          "Sign-off refused: " + (o.rejectionReason || "promotion blocked"),
        );
      }
    } catch (err) {
      setPromoteStatus("Sign-off failed (" + (err as Error).message + ").");
    }
  }

  const minScore =
    gate && gate.sources.length
      ? Math.min(...gate.sources.map((s) => s.score))
      : 0;

  const checks: [string, boolean, string][] = gate
    ? [
        [
          "Certificate valid",
          gate.certificate.present && !gate.certificate.expired,
          gate.certificate.id || "none",
        ],
        ["Policy bound", gate.policyBound, gate.policyVersion || "none"],
        ["Document sources ready", gate.sourcesReady, minScore + "% (lowest)"],
        [
          "Connections healthy",
          gate.connectorsHealthy,
          gate.connectorsHealthy ? "healthy" : "unhealthy",
        ],
        [
          "Human-judgement rule active",
          gate.humanJudgementActive,
          gate.humanJudgementActive ? "active" : "inactive",
        ],
        [
          "Within its trusted level",
          gate.ceilingOk,
          ceilingLabel(gate.certifiedCeiling),
        ],
      ]
    : [];

  return (
    <Screen id="gate" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Agent assurance</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>
          Supervised trial. Final go-live needs service-owner approval.
        </span>
      </div>
      <div className="mn">
        <div className="cap">Step 4 of 4 · Approve supervised trial</div>
        <h1>Go-live check</h1>
        <p className="lead">
          {error ? (
            "Could not reach Authority (" + error + ")."
          ) : gate ? (
            <>
              Promotion request: <b>housing appeals agent v3</b> to live, under
              certificate{" "}
              <span className="ref">{gate.certificate.id || "none"}</span>.
            </>
          ) : (
            "Loading promotion request from Authority…"
          )}
        </p>
        <div className="h2">Checks</div>
        <div>
          {checks.map((c) => (
            <div className="chk" key={c[0]}>
              <span
                className="mk"
                style={{ color: c[1] ? "#00703c" : "#d4351c" }}
              >
                {c[1] ? "✓" : "✕"}
              </span>
              <span className="tx">
                <b>{c[0]}</b>
              </span>
              <span className="rt">{c[2]}</span>
            </div>
          ))}
        </div>
        <div className="verdict">
          {gate &&
            (gate.promotable ? (
              <>
                <div className="vh">
                  <span className="tag">Allowed</span>
                  <span className="t">{gate.verdict}</span>
                </div>
                <div className="vb">
                  Cleared for <b>{ceilingLabel(gate.certifiedCeiling)}</b>.{" "}
                  {CEILING_EXPLAINER}
                </div>
              </>
            ) : (
              <>
                <div className="vh">
                  <span
                    className="tag"
                    style={{ background: "#d4351c", color: "#fff" }}
                  >
                    Blocked
                  </span>
                  <span className="t">{gate.verdict}</span>
                </div>
                <div className="vb">{gate.blockers.join(" ")}</div>
              </>
            ))}
        </div>
        <div className="nav2">
          <NavLink to="certificate" className="lk">
            ← Back
          </NavLink>
          <span style={{ display: "flex", gap: 18, alignItems: "center" }}>
            <NavLink to="sources" className="lk">
              Source readiness
            </NavLink>
            <span className="btn" onClick={promote} role="button">
              Promote to supervised live
            </span>
          </span>
        </div>
        {promoteStatus && (
          <div style={{ marginTop: 12, fontSize: 14, color: "#d4351c" }}>
            {promoteStatus}
          </div>
        )}
      </div>

      {promoted && (
        <div className="modal-back" onClick={() => setPromoted(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-tick">✓</div>
            <h2 style={{ margin: "0 0 6px" }}>Promoted to supervised live</h2>
            <p style={{ color: "#505a5f", margin: "0 0 14px", lineHeight: 1.5 }}>
              Signed off by <b>{promoted.approver}</b>, cleared for{" "}
              <b>{ceilingLabel(promoted.grantedCeiling)}</b>, for a supervised
              trial of {promoted.trialPeriod}. {CEILING_EXPLAINER}
            </p>
            <p style={{ color: "#505a5f", fontSize: 13, margin: "0 0 16px" }}>
              Recorded on the tamper-proof ledger.
            </p>
            <span
              className="gov-btn g-green"
              role="button"
              onClick={() => setPromoted(null)}
            >
              Done
            </span>
          </div>
        </div>
      )}
    </Screen>
  );
}
