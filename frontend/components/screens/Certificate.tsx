"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, CertificateReport, ukDate } from "@/lib/api";
import { useAgent } from "@/lib/agent";

const ACTION_LABELS: Record<string, string> = {
  draft_response: "Draft citizen responses",
  request_evidence: "Request missing documents",
  escalate: "Escalate to an officer",
  send_decision: "Send adverse citizen decisions",
  close_case: "Close housing appeal cases",
};
const actionLabel = (a: string) => ACTION_LABELS[a] || a;

export function CertificateScreen({ active }: { active: ScreenId }) {
  const { agentId } = useAgent();
  const [report, setReport] = useState<CertificateReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setReport(null);
    setError(null);
    authority
      .get<CertificateReport>(
        `/agents/${encodeURIComponent(agentId)}/certificate`,
      )
      .then(setReport)
      .catch((err) => setError(err.message));
  }, [agentId]);

  const cert = report?.certificate;

  return (
    <Screen id="certificate" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Agent assurance</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-g">Certificate issued</span>
        <span style={{ alignSelf: "center" }}>
          Step 3 of 4 · Issue certificate
        </span>
      </div>
      <div className="mn">
        <div className="cap">Result</div>
        <h1>Certificate issued</h1>
        <div className="cert">
          <div className="cth">
            <div>
              <div className="t">Agent assured for live service</div>
              <div className="nm">Housing appeals agent v3</div>
            </div>
            <div className="ref">{cert ? cert.id : "SWS-CERT"}</div>
          </div>
          <div className="cmeta">
            {error ? (
              "Could not reach Authority (" + error + ")."
            ) : cert ? (
              <>
                <span>
                  Assessed: <b>{ukDate(cert.issuedAt)}</b>
                </span>
                <span>
                  Signed off: <b>{cert.approver}</b>
                </span>
                <span>
                  Built by: <b>{cert.builder}</b>
                </span>
                <span>
                  Review by: <b>{ukDate(cert.expiresAt)}</b>
                </span>
              </>
            ) : (
              "Loading certificate from Authority…"
            )}
          </div>
          <div className="split">
            <div className="col granted">
              <div className="cl">Certified to</div>
              {cert?.certifiedActions.map((a) => (
                <div className="li" key={a}>
                  <span className="m">✓</span>
                  {actionLabel(a)}
                </div>
              ))}
            </div>
            <div className="col held">
              <div className="cl">Not certified to</div>
              {cert?.notCertifiedActions.map((a) => (
                <div className="li" key={a}>
                  <span className="m">✕</span>
                  {actionLabel(a)}
                </div>
              ))}
            </div>
          </div>
          <div className="ceil">
            {cert ? (
              <>
                <span className="lvl">
                  Approved ceiling: {cert.ceiling}{" "}
                  <small>low-risk action</small>
                </span>
                <span className="status">Ready for supervised live trial</span>
              </>
            ) : (
              <>
                <span className="lvl">Approved ceiling</span>
                <span className="status" />
              </>
            )}
          </div>
        </div>
        <div className="narrow">
          <b>
            The agent asked for broad permission. Assurance granted only the safe
            slice.
          </b>
          <div className="row">
            <span className="k">Requested</span> draft · request documents ·{" "}
            <span className="strike">send response</span> ·{" "}
            <span className="strike">close case</span>
          </div>
          <div className="row">
            <span className="k">Granted</span> <b>draft · request documents</b>
          </div>
          <div className="row">
            <span className="k">Held back</span>{" "}
            <span className="strike">send response · close case</span>, officer
            only
          </div>
        </div>
        <div className="nav2">
          <NavLink to="results" className="lk">
            ← Back
          </NavLink>
          <NavLink to="gate" className="btn">
            Apply certificate to deployment gate
          </NavLink>
        </div>
      </div>
    </Screen>
  );
}
