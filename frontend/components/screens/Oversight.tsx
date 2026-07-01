"use client";

import { useCallback, useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import {
  authority,
  CertificateReport,
  Incident,
  OversightMetrics,
  ukDate,
} from "@/lib/api";
import { useUser } from "@/lib/user";
import { useAgent } from "@/lib/agent";
import { ceilingLabel } from "@/lib/labels";

// The headline metrics worth showing, in plain English. Raw counters like
// ledger_rows / internal_errors are left off the dashboard.
const HEADLINE_METRICS: { k: string; l: string }[] = [
  { k: "decisions", l: "Decisions" },
  { k: "holds", l: "Held for a person" },
  { k: "blocks", l: "Blocked" },
  { k: "active_certificates", l: "Live assistants" },
  { k: "incidents", l: "Incidents" },
];

interface PolicyStanding {
  agentId: string;
  name: string;
  version: string;
  certificateStatus: string | null;
  certifiedAt: string | null;
  inForcePolicyVersion: string | null;
  standing: string;
}

const STANDING_LABELS: Record<string, string> = {
  CURRENT: "Current",
  REVIEW_REQUIRED: "Review required (policy changed)",
  CERTIFIED_UNDER_EARLIER_POLICY: "Certified under an earlier policy",
  EXPIRED: "Certificate expired",
  SUSPENDED: "Suspended",
  UNCERTIFIED: "Not certified",
};

export function Oversight({ active }: { active: ScreenId }) {
  const { user } = useUser();
  const { agentId } = useAgent();
  // Containment is the head of department's authority (Authority also enforces
  // this; the UI just reflects it).
  const canContain =
    user?.role === "HEAD_OF_DEPARTMENT" || user?.role === "ADMIN";
  const [metrics, setMetrics] = useState<OversightMetrics | null>(null);
  const [metricsError, setMetricsError] = useState<string | null>(null);
  const [agentStatus, setAgentStatus] = useState<{
    label: string;
    danger: boolean;
    ceiling?: string;
  } | null>(null);
  const [incidents, setIncidents] = useState<Incident[] | null>(null);
  const [incidentsError, setIncidentsError] = useState<string | null>(null);
  const [standings, setStandings] = useState<PolicyStanding[] | null>(null);
  const [status, setStatus] = useState("");
  const [recertStatus, setRecertStatus] = useState("");

  const loadIncidents = useCallback(() => {
    authority
      .get<Incident[]>("/incidents")
      .then(setIncidents)
      .catch((err) => setIncidentsError(err.message));
  }, []);

  const loadOversight = useCallback(() => {
    authority
      .get<OversightMetrics>("/oversight/metrics")
      .then(setMetrics)
      .catch((err) => setMetricsError(err.message));

    authority
      .get<PolicyStanding[]>("/oversight/policy-standing")
      .then(setStandings)
      .catch(() => {});

    authority
      .get<CertificateReport>(
        `/agents/${encodeURIComponent(agentId)}/certificate`,
      )
      .then((rep) => {
        if (rep && rep.certificate) {
          const st = rep.certificate.status;
          if (st === "ACTIVE") {
            setAgentStatus({
              label: "active",
              danger: false,
              ceiling: rep.certificate.ceiling,
            });
          } else {
            setAgentStatus({ label: st.toLowerCase(), danger: true });
          }
        } else {
          setAgentStatus({ label: "no certificate", danger: false });
        }
      })
      .catch(() => {});

    loadIncidents();
  }, [loadIncidents, agentId]);

  useEffect(() => {
    loadOversight();
  }, [loadOversight]);

  async function suspend() {
    setStatus("Raising an incident and containing the agent…");
    try {
      const i = await authority.post<Incident>("/incidents", {
        agentId: agentId,
        trigger: "HUMAN_REPORT",
        detail: "Suspended from the oversight screen",
        reportedBy: "Head of Housing Service",
      });
      const c = i.containment;
      setStatus(
        `Contained. Certificate ${c.suspendedCertificate || "none"} suspended, ${
          (c.revokedCapabilities || []).length
        } capability(ies) revoked, ${
          (c.heldCases || []).length
        } case(s) held. Ledgered. Re-certification required to return to live.`,
      );
      loadOversight();
    } catch (err) {
      setStatus("Suspend failed (" + (err as Error).message + ").");
    }
  }

  async function restrict() {
    setStatus("Restricting the send action…");
    try {
      const res = await authority.post<{ action: string }>(
        `/agents/${encodeURIComponent(agentId)}/restrict`,
        {
          action: "send_decision",
          reason: "Restricted from the oversight screen",
          reportedBy: "Head of Housing Service",
        },
      );
      setStatus(
        `Action ${res.action} restricted for the agent. Recorded in the ledger.`,
      );
    } catch (err) {
      setStatus("Restrict failed (" + (err as Error).message + ").");
    }
  }

  // One-click recovery: re-run the safety test for every flagged assistant and,
  // on a pass, lift the flag. This closes the loop after a policy change.
  async function recertify() {
    const flagged = (standings || []).filter((s) => s.standing !== "CURRENT");
    if (!flagged.length) return;
    setRecertStatus(
      `Re-running the safety test for ${flagged.length} assistant(s)…`,
    );
    try {
      for (const s of flagged) {
        await authority.post(`/agents/${encodeURIComponent(s.agentId)}/certify`, {
          builder: "SwarmSight assurance run",
          approver: user?.displayName || "Head of Housing Service",
        });
      }
      setRecertStatus("Re-certified against the current rules. Flags cleared.");
      loadOversight();
    } catch (err) {
      setRecertStatus("Re-certification failed (" + (err as Error).message + ").");
    }
  }

  const flaggedCount = (standings || []).filter(
    (s) => s.standing !== "CURRENT",
  ).length;

  return (
    <Screen id="oversight" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Oversight</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>Live oversight. If an agent misbehaves, contain it at once.</span>
      </div>
      <div className="mn">
        <div className="cap">Head of department</div>
        <h1>Department oversight</h1>
        <p className="lead">
          Live metrics from the ledger, and the controls to restrict an action or
          suspend an agent. Containment is immediate and fully recorded.
        </p>
        {metricsError ? (
          <div style={{ marginTop: 18 }}>
            Could not reach Authority ({metricsError}).
          </div>
        ) : (
          <div className="ctl-strip">
            {HEADLINE_METRICS.map((x) => (
              <div className="ctl-metric" key={x.k}>
                <div className="m-n">{metrics ? (metrics[x.k] ?? 0) : "…"}</div>
                <div className="m-l">{x.l}</div>
              </div>
            ))}
          </div>
        )}
        {canContain && flaggedCount > 0 && (
          <div
            className="ctl-panel"
            style={{ borderTopColor: "#d4351c", marginTop: 18 }}
          >
            <b>
              {flaggedCount} assistant{flaggedCount > 1 ? "s" : ""} flagged after
              a policy change.
            </b>{" "}
            Their safety test was done under the old rules. Re-run the test to
            trust them under the new rules.
            <div style={{ marginTop: 12 }}>
              <span className="gov-btn g-green" role="button" onClick={recertify}>
                Re-certify flagged assistants →
              </span>
              <span
                style={{ marginLeft: 12, fontSize: 13, color: "#505a5f" }}
              >
                {recertStatus}
              </span>
            </div>
          </div>
        )}
        <div className="h2" style={{ marginTop: 22 }}>
          The live assistant
        </div>
        <div className="sl" style={{ marginTop: 12 }}>
          <div className="r">
            <span className="k">Housing appeals assistant v3</span>
            <span className="vv">
              {agentStatus ? (
                agentStatus.danger ? (
                  <span
                    className="tag"
                    style={{ background: "#d4351c", color: "#fff" }}
                  >
                    {agentStatus.label}
                  </span>
                ) : (
                  <span className="tag t-g">
                    running · {ceilingLabel(agentStatus.ceiling)}
                  </span>
                )
              ) : (
                "checking…"
              )}
            </span>
          </div>

          <div style={{ marginTop: 14, fontWeight: 700 }}>
            Stop or restrict this assistant
          </div>
          {canContain ? (
            <>
              <div className="src" style={{ marginTop: 8 }}>
                <span className="l">Restrict one action</span>: stop it doing a
                single thing (like sending), it keeps running for everything
                else.
              </div>
              <div className="src">
                <span className="l">Suspend</span>: take it fully offline now.
                It needs to pass assurance again to return.
              </div>
              <div style={{ marginTop: 12 }}>
                <span className="btn sec" onClick={restrict} role="button">
                  Restrict sending
                </span>
                <span
                  className="btn"
                  style={{ background: "#d4351c" }}
                  onClick={suspend}
                  role="button"
                >
                  Suspend the assistant
                </span>
              </div>
              <div style={{ marginTop: 8, fontSize: 13, color: "#505a5f" }}>
                Either takes effect immediately and is recorded on the ledger.
              </div>
            </>
          ) : (
            <div style={{ fontSize: 14, color: "#505a5f", marginTop: 6 }}>
              Only the head of department can stop or restrict an assistant. (Use
              the account menu&apos;s &ldquo;View as&rdquo; to see it as the head.)
            </div>
          )}
          <div style={{ marginTop: 12 }}>
            <NavLink to="agentlog" className="lk">
              View per-agent log
            </NavLink>
          </div>
          <div style={{ marginTop: 12, fontSize: 14, color: "#505a5f" }}>
            {status}
          </div>
        </div>
        <div className="h2" style={{ marginTop: 22 }}>
          Incidents
        </div>
        <div style={{ marginTop: 12 }}>
          {incidentsError ? (
            "Could not reach Authority (" + incidentsError + ")."
          ) : !incidents ? (
            "Loading…"
          ) : incidents.length === 0 ? (
            "No incidents."
          ) : (
            incidents.map((i, idx) => {
              const c = i.containment;
              return (
                <div className="sl" style={{ marginBottom: 12 }} key={idx}>
                  <div className="r">
                    <span className="k">
                      {i.trigger} <span className="tag t-y">{i.status}</span>
                    </span>
                    <span className="vv">{i.agentId}</span>
                  </div>
                  <div style={{ marginTop: 8 }}>{i.detail || ""}</div>
                  <div className="src" style={{ color: "#505a5f" }}>
                    Suspended {c.suspendedCertificate || "none"} · revoked{" "}
                    {(c.revokedCapabilities || []).length} capability(ies) · held{" "}
                    {(c.heldCases || []).length} case(s) · service owner notified
                  </div>
                </div>
              );
            })
          )}
        </div>

        <div className="h2" style={{ marginTop: 22 }}>
          Policy standing <span className="c">agents vs the policy in force</span>
        </div>
        <div style={{ marginTop: 12 }}>
          {!standings ? (
            "Loading…"
          ) : standings.length === 0 ? (
            "No agents registered."
          ) : (
            <table>
              <tbody>
                <tr>
                  <th>Agent</th>
                  <th>Certified</th>
                  <th>Policy in force</th>
                  <th>Standing</th>
                </tr>
                {standings.map((s) => {
                  const ok = s.standing === "CURRENT";
                  return (
                    <tr key={s.agentId}>
                      <td>
                        {s.name} {s.version}
                      </td>
                      <td>
                        {s.certifiedAt ? ukDate(s.certifiedAt) : "n/a"}
                      </td>
                      <td>{s.inForcePolicyVersion || "n/a"}</td>
                      <td>
                        <span
                          className={"pill " + (ok ? "p-strong" : "p-missing")}
                          style={
                            ok
                              ? { background: "#cce2d8", color: "#005a30" }
                              : { background: "#f6d7d2", color: "#942514" }
                          }
                        >
                          {STANDING_LABELS[s.standing] || s.standing}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          <div style={{ marginTop: 10, fontSize: 13, color: "#505a5f" }}>
            Decisions always resolve under the policy version in force at their
            timestamp. A non-current standing means the agent&apos;s certificate
            no longer matches that policy and re-certification is due.
          </div>
        </div>
      </div>
    </Screen>
  );
}
