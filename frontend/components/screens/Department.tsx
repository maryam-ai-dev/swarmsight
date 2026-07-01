"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { ScreenId, useNav } from "@/lib/nav";
import { authority } from "@/lib/api";
import { useAgent } from "@/lib/agent";
import { Role, ROLE_LABELS } from "@/lib/user";

interface DirectoryUser {
  id: string;
  email: string;
  role: Role;
  displayName: string;
  active: boolean;
}
interface PolicyStanding {
  agentId: string;
  name: string;
  version: string;
  standing: string;
  certificateStatus: string | null;
}

const ROLE_ORDER: Role[] = [
  "OFFICER",
  "HEAD_OF_DEPARTMENT",
  "SERVICE_OWNER",
  "ADMIN",
];
const ROLE_CLASS: Record<Role, string> = {
  OFFICER: "",
  HEAD_OF_DEPARTMENT: "r-head",
  SERVICE_OWNER: "r-owner",
  ADMIN: "r-admin",
};
// What each account type does, and the screen that is its primary view.
const ROLE_VIEW: Record<Role, { does: string; screen: ScreenId | "/admin" }> = {
  OFFICER: { does: "Works the caseload; makes every citizen decision.", screen: "officer" },
  HEAD_OF_DEPARTMENT: { does: "Oversees the agents; can restrict or suspend.", screen: "oversight" },
  SERVICE_OWNER: { does: "Assures and signs off agents for go-live.", screen: "check" },
  ADMIN: { does: "Manages accounts and roles.", screen: "/admin" },
};

const STANDING_LABELS: Record<string, string> = {
  CURRENT: "Current",
  REVIEW_REQUIRED: "Review required",
  CERTIFIED_UNDER_EARLIER_POLICY: "Under earlier policy",
  EXPIRED: "Expired",
  SUSPENDED: "Suspended",
  UNCERTIFIED: "Not certified",
};

// The governed workflows in the housing department, and the point a human owns.
const WORKFLOWS = [
  { name: "Housing appeals", policy: "HA-09", human: "An officer makes the decision; the agent only drafts and checks." },
  { name: "Homelessness assessment", policy: "HA-09", human: "An officer decides priority need; the agent triages and requests evidence." },
  { name: "FOI triage", policy: "HA-09", human: "An officer approves any release; the agent classifies and redacts for review." },
];

export function Department({ active }: { active: ScreenId }) {
  const { go } = useNav();
  const { agents } = useAgent();
  const [metrics, setMetrics] = useState<Record<string, number> | null>(null);
  const [directory, setDirectory] = useState<DirectoryUser[] | null>(null);
  const [standings, setStandings] = useState<PolicyStanding[] | null>(null);

  useEffect(() => {
    authority.get<Record<string, number>>("/oversight/metrics").then(setMetrics).catch(() => {});
    authority.get<DirectoryUser[]>("/auth/directory").then(setDirectory).catch(() => {});
    authority.get<PolicyStanding[]>("/oversight/policy-standing").then(setStandings).catch(() => {});
  }, []);

  const m = metrics || {};
  const standingFor = (id: string) => standings?.find((s) => s.agentId === id);

  function openView(screen: ScreenId | "/admin") {
    if (screen === "/admin") window.location.href = "/admin";
    else go(screen);
  }

  const METRICS: { key: string; label: string }[] = [
    { key: "decisions", label: "Decisions" },
    { key: "holds", label: "Held for a human" },
    { key: "blocks", label: "Blocked" },
    { key: "active_certificates", label: "Live agents" },
    { key: "incidents", label: "Incidents" },
  ];

  return (
    <Screen id="department" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Kensington &amp; Chelsea</span>
        </span>
      </div>
      <div className="mn">
        <span className="ctl-crumb" role="button" onClick={() => go("departments")}>
          ← All boroughs
        </span>
        <h1>Royal Borough of Kensington and Chelsea</h1>
        <p className="lead">
          Housing service: appeals, homelessness, and FOI triage. The assistants
          prepare and check the work against the borough&apos;s own policy; a
          person makes every decision that affects a citizen.
        </p>

        {/* Live metrics from the ledger */}
        <div className="ctl-strip">
          {METRICS.map((x) => (
            <div className="ctl-metric" key={x.key}>
              <div className="m-n">{m[x.key] ?? 0}</div>
              <div className="m-l">{x.label}</div>
            </div>
          ))}
        </div>

        {/* Accounts, the four account types, who holds them, and their view */}
        <div className="ctl-panel">
          <h2>Accounts in this department</h2>
          <div className="sub">
            Four roles. Each opens straight into the view it owns.
          </div>
          {!directory ? (
            "Loading the directory…"
          ) : (
            ROLE_ORDER.flatMap((role) => {
              const people = directory.filter((u) => u.role === role);
              if (people.length === 0) return [];
              return people.map((u) => (
                <div className="ctl-row" key={u.id}>
                  <span className={`ctl-role ${ROLE_CLASS[role]}`}>
                    {ROLE_LABELS[role]}
                  </span>
                  <span className="ctl-main">
                    <span className="nm">{u.displayName}</span>
                    {!u.active && (
                      <span style={{ color: "#d4351c" }}> · inactive</span>
                    )}
                    <span className="dt">
                      {u.email}, {ROLE_VIEW[role].does}
                    </span>
                  </span>
                  <button
                    className="ctl-open"
                    onClick={() => openView(ROLE_VIEW[role].screen)}
                  >
                    Open their view →
                  </button>
                </div>
              ));
            })
          )}
        </div>

        {/* Workflows, what is being run, and where the human owns the decision */}
        <div className="ctl-panel">
          <h2>Workflows running here</h2>
          <div className="sub">
            Every workflow is governed by Housing&apos;s policy and holds for a
            human at the decision point.
          </div>
          {WORKFLOWS.map((w) => (
            <div className="ctl-row" key={w.name}>
              <span className="ctl-main">
                <span className="nm">{w.name}</span>
                <span className="dt">
                  Policy {w.policy} ·{" "}
                  <span className="human-mark" />
                  {w.human}
                </span>
              </span>
              <button className="ctl-open" onClick={() => go("policies")}>
                Policy →
              </button>
              <button className="ctl-open" onClick={() => go("officer")}>
                Caseload →
              </button>
            </div>
          ))}
        </div>

        {/* Agents, what is doing the work, and its assurance standing */}
        <div className="ctl-panel">
          <h2>Agents doing the work</h2>
          <div className="sub">
            Each agent is assured against the policy before it runs, and its
            standing is watched as policy changes.
          </div>
          {agents.length === 0 ? (
            "No agents registered."
          ) : (
            agents.map((a) => {
              const s = standingFor(a.id);
              return (
                <div className="ctl-row" key={a.id}>
                  <span className="ctl-main">
                    <span className="nm">
                      {a.name}{" "}
                      <span style={{ color: "#505a5f", fontWeight: 400 }}>
                        {a.version}
                      </span>
                    </span>
                    <span className="dt">
                      {s
                        ? `Standing: ${STANDING_LABELS[s.standing] || s.standing}`
                        : "Standing: unknown"}{" "}
                      · {a.active ? "active" : "inactive"}
                    </span>
                  </span>
                  <button className="ctl-open" onClick={() => go("check")}>
                    Assurance →
                  </button>
                  <button className="ctl-open" onClick={() => go("agentlog")}>
                    Log →
                  </button>
                </div>
              );
            })
          )}
        </div>

        {/* Where a human is in the loop */}
        <div className="ctl-panel">
          <h2>
            <span className="human-mark" />
            Where a human is in the loop
          </h2>
          <div className="sub">
            The agent can prepare and check, but it cannot act on a citizen alone.
          </div>
          <div className="ctl-row">
            <span className="ctl-main">
              <span className="nm">An officer approves every citizen decision</span>
              <span className="dt">
                The agent drafts; the officer edits and approves. Nothing is sent
                without it.
              </span>
            </span>
            <button className="ctl-open" onClick={() => go("officer")}>
              Officer →
            </button>
          </div>
          <div className="ctl-row">
            <span className="ctl-main">
              <span className="nm">The head of department can contain an agent</span>
              <span className="dt">
                Restrict an action or suspend an agent at any time; it is recorded.
              </span>
            </span>
            <button className="ctl-open" onClick={() => go("oversight")}>
              Oversight →
            </button>
          </div>
          <div className="ctl-row">
            <span className="ctl-main">
              <span className="nm">A service owner signs off go-live</span>
              <span className="dt">
                An agent reaches a live service only after assurance and a
                separate approval.
              </span>
            </span>
            <button className="ctl-open" onClick={() => go("gate")}>
              Go-live →
            </button>
          </div>
          <div className="ctl-row">
            <span className="ctl-main">
              <span className="nm">Every step is provable</span>
              <span className="dt">
                Each decision, edit, and approval is on the tamper-evident ledger.
              </span>
            </span>
            <button className="ctl-open" onClick={() => go("audit")}>
              Proof pack →
            </button>
          </div>
        </div>
      </div>
    </Screen>
  );
}
