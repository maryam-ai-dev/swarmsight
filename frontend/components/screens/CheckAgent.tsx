"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId, useNav } from "@/lib/nav";
import { authority, ArenaRun, CapabilityProposal } from "@/lib/api";
import { useAgent } from "@/lib/agent";
import { humanize } from "@/lib/labels";

const ACTION_LABELS: Record<string, string> = {
  draft_response: "Draft a citizen response",
  request_evidence: "Request missing evidence",
  escalate: "Escalate to an officer",
  send_decision: "Send a citizen-facing response",
  close_case: "Close the case",
};

// Sensible defaults so the capability checkboxes are visible straight away;
// "Derive capabilities" then refines them from the description with Claude.
const DEFAULT_CAPS: CapabilityProposal[] = [
  { action: "draft_response", label: "Draft a response for officer review", citizenFacing: false, recommendedAllow: true, rationale: "Safe internal action." },
  { action: "request_evidence", label: "Request missing evidence", citizenFacing: false, recommendedAllow: true, rationale: "Safe internal action." },
  { action: "escalate", label: "Escalate to an officer", citizenFacing: false, recommendedAllow: true, rationale: "Safe internal action." },
  { action: "summarise_case", label: "Summarise / triage a case", citizenFacing: false, recommendedAllow: true, rationale: "Safe internal action." },
  { action: "send_decision", label: "Send a decision to the citizen", citizenFacing: true, recommendedAllow: false, rationale: "Citizen-facing: a person takes this, not the assistant." },
  { action: "close_case", label: "Close a case", citizenFacing: true, recommendedAllow: false, rationale: "Citizen-facing: a person takes this." },
  { action: "release_records", label: "Release records", citizenFacing: true, recommendedAllow: false, rationale: "Citizen-facing: a person takes this." },
];

export function CheckAgent({ active }: { active: ScreenId }) {
  const { go } = useNav();
  const { selected, agentId } = useAgent();
  const [status, setStatus] = useState("");

  // --- Live assurance from intent: describe -> capabilities -> generated suite ---
  const [description, setDescription] = useState(
    "Help housing officers by reading an applicant's documents, drafting a response for officer review, requesting missing evidence, and escalating eviction-risk cases. It must never send a decision to a citizen on its own.",
  );
  const [caps, setCaps] = useState<CapabilityProposal[] | null>(null);
  const [allow, setAllow] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState("");
  const [genResult, setGenResult] = useState<ArenaRun | null>(null);
  const [matched, setMatched] = useState<{
    workflowId: string;
    workflowName: string;
    rationale: string;
    live: boolean;
  } | null>(null);
  // The department policy the arena runs against: the matched workflow if the
  // task has been matched, else the agent's assigned workflow.
  const workflow = matched?.workflowId || selected?.workflow || "HA-09";

  async function matchWorkflow() {
    setBusy("Matching the task to the department's policies…");
    setMatched(null);
    try {
      const m = await authority.post<{
        workflowId: string;
        workflowName: string;
        rationale: string;
        live: boolean;
      }>("/workflows/match", { description });
      setMatched(m);
      setBusy("");
    } catch (e) {
      setBusy("Could not match a policy (" + (e as Error).message + ").");
    }
  }

  // Show the default capability checkboxes immediately (before any Claude call).
  useEffect(() => {
    setCaps(DEFAULT_CAPS);
    const init: Record<string, boolean> = {};
    DEFAULT_CAPS.forEach((p) => (init[p.action] = p.recommendedAllow));
    setAllow(init);
  }, []);

  async function deriveCapabilities() {
    setBusy("Deriving capabilities from the description…");
    setGenResult(null);
    try {
      const proposals = await authority.post<CapabilityProposal[]>(
        "/agents/capabilities/derive",
        { description, policyId: workflow },
      );
      setCaps(proposals);
      const init: Record<string, boolean> = {};
      proposals.forEach((p) => (init[p.action] = p.recommendedAllow));
      setAllow(init);
      setBusy("");
    } catch (e) {
      setBusy("Could not derive capabilities (" + (e as Error).message + ").");
    }
  }

  async function runGenerated() {
    setBusy("Generating policy-derived scenarios and running them live…");
    setGenResult(null);
    try {
      const allowedActions = (caps || [])
        .filter((p) => allow[p.action])
        .map((p) => p.action);
      const forbiddenActions = (caps || [])
        .filter((p) => !allow[p.action])
        .map((p) => p.action);
      const r = await authority.post<ArenaRun>(
        `/agents/${encodeURIComponent(agentId)}/assurance/run`,
        { policyId: workflow, allowedActions, forbiddenActions },
      );
      setGenResult(r);
      setBusy("");
    } catch (e) {
      setBusy("Generated assurance failed (" + (e as Error).message + ").");
    }
  }

  // Runs the arena against the selected agent's own endpoint, then advances.
  async function runCheck() {
    setStatus("Running scenarios against the agent's endpoint…");
    try {
      const a = await authority.post<ArenaRun>(
        `/agents/${encodeURIComponent(agentId)}/arena/run`,
      );
      setStatus(
        `Live result: safety ${a.safetyPass ? "passed" : "failed"}, usefulness ${Math.round(
          a.usefulnessScore * 100,
        )}%, recommended ceiling ${a.recommendedCeiling}.`,
      );
      go("results");
    } catch (err) {
      setStatus("Live check failed (" + (err as Error).message + ").");
    }
  }

  return (
    <Screen id="check" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Agent assurance</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>
          This assurance result is for a supervised trial. Final deployment needs
          service-owner approval.
        </span>
      </div>
      <div className="mn">
        <div className="cap">Step 1 of 4 · Check agent</div>
        <h1>Is this agent safe to use in a live service?</h1>
        <p className="lead">
          Before it goes near real casework, the agent is tested against
          realistic cases, your policy, and your documents.
        </p>
        <p style={{ margin: "6px 0 0" }}>
          <NavLink to="agents" className="lk">
            Register or switch agent →
          </NavLink>
        </p>
        <div className="card">
          <div className="ch">
            {selected ? selected.name : "No agent selected"}{" "}
            <span style={{ color: "#505a5f", fontWeight: 400 }}>
              {selected?.version}
            </span>
            <small>
              {selected
                ? `Endpoint: ${selected.endpointUrl} · Governed by department policy ${selected.workflow}`
                : "Pick an agent in the registry first."}
            </small>
          </div>
          <div className="cb">
            <div className="pl">Permissions it is asking for</div>
            {(selected?.requestedActions || []).map((a) => (
              <div className="perm" key={a}>
                {ACTION_LABELS[a] || a}
              </div>
            ))}
            <span className="btn" onClick={runCheck} role="button">
              Run assurance check
            </span>
            <div style={{ marginTop: 12, fontSize: 14, color: "#505a5f" }}>
              {status}
            </div>
          </div>
        </div>

        <div className="card" style={{ marginTop: 22 }}>
          <div className="ch">
            Live assurance from intent{" "}
            <span className="tag t-y">generated</span>
            <small>
              Describe what the agent should do. We derive its capabilities, then
              generate scenarios from your policy (HA-09), one per guard, plus
              adversarial cases, and run them against the agent&apos;s endpoint.
            </small>
          </div>
          <div className="cb">
            <div className="pl">What should this agent do?</div>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={4}
              style={{
                width: "100%",
                padding: "8px 10px",
                border: "2px solid #0b0c0c",
                fontSize: 14,
                fontFamily: "inherit",
              }}
            />
            <div style={{ marginTop: 10, display: "flex", gap: 10, flexWrap: "wrap" }}>
              <span className="btn sec" role="button" onClick={matchWorkflow}>
                1 · Match to a department policy →
              </span>
              <span className="btn" role="button" onClick={deriveCapabilities}>
                2 · Derive capabilities →
              </span>
            </div>
            {matched && (
              <div
                className="note"
                style={{ marginTop: 12, borderLeftColor: "#00703c" }}
              >
                Assigned to the department&apos;s{" "}
                <b>
                  {matched.workflowName} ({matched.workflowId})
                </b>{" "}
                workflow. {matched.rationale}{" "}
                <span style={{ color: "#505a5f" }}>
                  ({matched.live ? "matched by Claude" : "matched offline"}) — the
                  arena will test against this policy.
                </span>
              </div>
            )}

            {caps && (
              <div style={{ marginTop: 14 }}>
                <div className="pl">
                  Capabilities to grant (citizen-facing actions stay off)
                </div>
                {caps.map((p) => (
                  <label
                    key={p.action}
                    className="perm"
                    style={{ display: "block", cursor: "pointer" }}
                  >
                    <input
                      type="checkbox"
                      checked={!!allow[p.action]}
                      onChange={(e) =>
                        setAllow({ ...allow, [p.action]: e.target.checked })
                      }
                      style={{ marginRight: 8 }}
                    />
                    {p.label}{" "}
                    {p.citizenFacing && (
                      <span
                        className="tag"
                        style={{ background: "#d4351c", color: "#fff" }}
                      >
                        citizen-facing
                      </span>
                    )}
                    <small style={{ display: "block", color: "#505a5f" }}>
                      {p.rationale}
                    </small>
                  </label>
                ))}
                <div style={{ marginTop: 12 }}>
                  <span className="btn" role="button" onClick={runGenerated}>
                    Generate scenarios &amp; run live →
                  </span>
                </div>
              </div>
            )}

            {genResult && (
              <div
                style={{
                  marginTop: 14,
                  border: "2px solid #0b0c0c",
                  padding: "12px 14px",
                  background: "#fff",
                }}
              >
                <b>
                  {genResult.overallPass ? "✓ Passed" : "✗ Failed"} ·{" "}
                  {genResult.results?.length || 0} scenarios · safety{" "}
                  {genResult.safetyPass ? "passed" : "failed"} · usefulness{" "}
                  {Math.round(genResult.usefulnessScore * 100)}% · ceiling{" "}
                  {genResult.recommendedCeiling}
                </b>
                <div style={{ marginTop: 8 }}>
                  {(genResult.results || []).map((r) => (
                    <div
                      className="src"
                      key={r.scenarioId}
                      style={{ alignItems: "flex-start" }}
                    >
                      <span>
                        <span className="l">
                          {r.safe ? "✓" : "✗"} {r.name}
                        </span>{" "}
                        <span style={{ color: "#505a5f" }}>
                          → the assistant chose to {humanize(r.proposedAction).toLowerCase()}
                          {" · "}case was {humanize(r.verdictEffect)}
                        </span>
                        <small style={{ display: "block", color: "#505a5f" }}>
                          {r.note}
                        </small>
                      </span>
                    </div>
                  ))}
                </div>
                <div style={{ marginTop: 8, fontSize: 13, color: "#505a5f" }}>
                  Cleared to:{" "}
                  {(genResult.certifiedActions || [])
                    .map((a) => humanize(a))
                    .join(", ") || "none"}{" "}
                  · Never allowed:{" "}
                  {(genResult.notCertifiedActions || [])
                    .map((a) => humanize(a))
                    .join(", ") || "none"}
                </div>
              </div>
            )}

            <div style={{ marginTop: 12, fontSize: 14, color: "#505a5f" }}>
              {busy}
            </div>
          </div>
        </div>
      </div>
    </Screen>
  );
}
