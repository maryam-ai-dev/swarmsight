"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, LedgerRow, shortHash } from "@/lib/api";
import { useAgent } from "@/lib/agent";
import { actorKind, humanize } from "@/lib/labels";

export function AgentLog({ active }: { active: ScreenId }) {
  const { agentId } = useAgent();
  const [rows, setRows] = useState<LedgerRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setRows(null);
    setError(null);
    authority
      .get<LedgerRow[]>(`/agents/${encodeURIComponent(agentId)}/log`)
      .then(setRows)
      .catch((err) => setError(err.message));
  }, [agentId]);

  return (
    <Screen id="agentlog" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Oversight</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>Every action this agent took, from the ledger.</span>
      </div>
      <div className="mn">
        <div className="crumb">
          <NavLink to="oversight" className="lk">
            Department oversight
          </NavLink>{" "}
          › Per-agent log
        </div>
        <h1>Per-agent log: housing appeals agent v3</h1>
        <p className="lead">
          The agent&apos;s ledger rows, newest first. Every decision, capture,
          certificate, deployment, and containment event, hash-chained and
          provable.
        </p>
        <div style={{ marginTop: 18 }}>
          {error ? (
            "Could not reach Authority (" + error + ")."
          ) : !rows ? (
            "Loading the agent log from Authority…"
          ) : rows.length === 0 ? (
            "No ledger rows for this agent yet."
          ) : (
            <table>
              <tbody>
                <tr>
                  <th>seq</th>
                  <th>who</th>
                  <th>action</th>
                  <th>case</th>
                  <th>actor</th>
                  <th>row hash</th>
                </tr>
                {rows.map((r, i) => (
                  <tr key={i}>
                    <td>{r.seq}</td>
                    <td>
                      <span
                        className={"who k-" + actorKind(r.intent).toLowerCase()}
                      >
                        {actorKind(r.intent)}
                      </span>
                    </td>
                    <td>{humanize(r.intent)}</td>
                    <td>{r.caseRef}</td>
                    <td>{r.actor}</td>
                    <td className="mono">{shortHash(r.rowHash)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </Screen>
  );
}
