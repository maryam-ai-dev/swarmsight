"use client";

import { useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId, useNav } from "@/lib/nav";
import { useAgent } from "@/lib/agent";
import { useUser } from "@/lib/user";

const AVAILABLE_ACTIONS: { id: string; label: string }[] = [
  { id: "draft_response", label: "Draft citizen responses" },
  { id: "request_evidence", label: "Request missing documents" },
  { id: "escalate", label: "Escalate to an officer" },
  { id: "send_decision", label: "Send adverse citizen decisions" },
  { id: "close_case", label: "Close cases" },
];

export function Agents({ active }: { active: ScreenId }) {
  const { go } = useNav();
  const { agents, agentId, setAgentId, reload, loading } = useAgent();
  const { user } = useUser();
  const canRegister =
    user?.role === "SERVICE_OWNER" || user?.role === "ADMIN";

  const [name, setName] = useState("");
  const [version, setVersion] = useState("v1");
  const [endpointUrl, setEndpointUrl] = useState("");
  const [environment, setEnvironment] = useState("gov-uk-prod");
  const [actions, setActions] = useState<string[]>(["draft_response", "escalate"]);
  const [status, setStatus] = useState("");
  const [secret, setSecret] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function toggle(id: string) {
    setActions((a) => (a.includes(id) ? a.filter((x) => x !== id) : [...a, id]));
  }

  async function register(e: React.FormEvent) {
    e.preventDefault();
    setStatus("");
    setSecret(null);
    setBusy(true);
    try {
      const r = await fetch("/_authority/agents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          version,
          endpointUrl,
          environment,
          requestedActions: actions,
        }),
      });
      const data = await r.json().catch(() => ({}));
      if (!r.ok) {
        setStatus(data.error || "Could not register (HTTP " + r.status + ")");
      } else {
        setStatus(`Registered ${data.agent.name} (${data.agent.id}).`);
        setSecret(data.callSecret);
        setName("");
        setEndpointUrl("");
        reload();
        setAgentId(data.agent.id);
      }
    } catch {
      setStatus("Could not reach the server");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen id="agents" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Agents</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>
          Register an agent at its own endpoint. SwarmSight governs it: it only
          proposes, Authority decides.
        </span>
      </div>
      <div className="mn">
        <div className="cap">Agent registry</div>
        <h1>Agents</h1>
        <p className="lead">
          Each agent is a service you run, reached at its own HTTP endpoint. Pick
          one to take through assurance, or register a new one.
        </p>

        <div className="h2">
          Registered agents{" "}
          <span className="c">{loading ? "" : agents.length}</span>
        </div>
        <div style={{ marginTop: 12 }}>
          {loading ? (
            "Loading agents from Authority…"
          ) : agents.length === 0 ? (
            "No agents registered yet."
          ) : (
            agents.map((a) => {
              const isSel = a.id === agentId;
              return (
                <div
                  className="sl"
                  style={{ marginBottom: 14 }}
                  key={a.id}
                >
                  <div className="r">
                    <span className="k">
                      {a.name} <span style={{ color: "#505a5f" }}>{a.version}</span>
                      {isSel && <span className="tag t-g">selected</span>}
                    </span>
                    <span className="vv">{a.environment}</span>
                  </div>
                  <div className="src" style={{ color: "#505a5f" }}>
                    <span className="l">Endpoint</span>{" "}
                    <span style={{ fontFamily: "ui-monospace, monospace" }}>
                      {a.endpointUrl}
                    </span>
                  </div>
                  <div className="src" style={{ color: "#505a5f" }}>
                    <span className="l">Requested</span>{" "}
                    {a.requestedActions.join(", ")}
                  </div>
                  <div style={{ marginTop: 10 }}>
                    {isSel ? (
                      <span className="btn" onClick={() => go("check")} role="button">
                        Take through assurance →
                      </span>
                    ) : (
                      <span
                        className="btn sec"
                        onClick={() => setAgentId(a.id)}
                        role="button"
                      >
                        Select
                      </span>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {canRegister && (
          <div className="h2" style={{ marginTop: 28 }}>
            Register an agent
          </div>
        )}
        {canRegister && (
          <form onSubmit={register} style={{ marginTop: 14, maxWidth: 560 }}>
            <Field label="Agent name">
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Benefits triage agent"
                required
                style={inputStyle}
              />
            </Field>
            <div style={{ display: "flex", gap: 14 }}>
              <Field label="Version">
                <input
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                  required
                  style={inputStyle}
                />
              </Field>
              <Field label="Environment">
                <input
                  value={environment}
                  onChange={(e) => setEnvironment(e.target.value)}
                  required
                  style={inputStyle}
                />
              </Field>
            </div>
            <Field label="Endpoint URL (the agent's /agent/act)">
              <input
                value={endpointUrl}
                onChange={(e) => setEndpointUrl(e.target.value)}
                placeholder="https://your-agent.example.gov.uk/agent/act"
                required
                style={inputStyle}
              />
            </Field>
            <div style={{ marginBottom: 14 }}>
              <span style={{ display: "block", fontWeight: 700, marginBottom: 6 }}>
                Permissions it is asking for
              </span>
              {AVAILABLE_ACTIONS.map((a) => (
                <label
                  key={a.id}
                  style={{ display: "block", fontSize: 15, padding: "3px 0" }}
                >
                  <input
                    type="checkbox"
                    checked={actions.includes(a.id)}
                    onChange={() => toggle(a.id)}
                    style={{ marginRight: 8 }}
                  />
                  {a.label}
                </label>
              ))}
            </div>
            <button className="btn" type="submit" disabled={busy}>
              {busy ? "Registering…" : "Register agent"}
            </button>
            {status && (
              <div style={{ marginTop: 12, fontSize: 14, color: "#505a5f" }}>
                {status}
              </div>
            )}
            {secret && (
              <div
                className="certline"
                style={{ marginTop: 12, borderLeftColor: "#00703c" }}
              >
                <b>Call secret (shown once):</b>{" "}
                <span style={{ fontFamily: "ui-monospace, monospace" }}>
                  {secret}
                </span>
                <br />
                Configure your agent to require this as a{" "}
                <code>Authorization: Bearer</code> header. SwarmSight sends it on
                every call so your agent can trust the caller.
              </div>
            )}
          </form>
        )}

        <div className="nav2">
          <NavLink to="check" className="lk">
            ← Back to assurance
          </NavLink>
        </div>
      </div>
    </Screen>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label style={{ display: "block", marginBottom: 14, flex: 1 }}>
      <span style={{ display: "block", fontWeight: 700, marginBottom: 5 }}>
        {label}
      </span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  fontSize: 16,
  padding: "9px 11px",
  border: "2px solid #0b0c0c",
  fontFamily: "inherit",
  background: "#fff",
};
