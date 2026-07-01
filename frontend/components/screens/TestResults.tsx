"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, ArenaScenario, CertificateReport } from "@/lib/api";
import { useAgent } from "@/lib/agent";

export function TestResults({ active }: { active: ScreenId }) {
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

  const header = error
    ? "Could not reach Authority (" + error + ")."
    : report
      ? `Tested against ${report.arenaSummary.results.length} supervised scenarios`
      : "Loading assurance results from Authority…";

  // Group scenarios by category, preserving first-seen order.
  const groups: { category: string; scenarios: ArenaScenario[] }[] = [];
  if (report) {
    for (const s of report.arenaSummary.results) {
      let g = groups.find((x) => x.category === s.category);
      if (!g) {
        g = { category: s.category, scenarios: [] };
        groups.push(g);
      }
      g.scenarios.push(s);
    }
  }

  return (
    <Screen id="results" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Agent assurance</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>Supervised trial assurance result.</span>
      </div>
      <div className="mn">
        <div className="cap">Step 2 of 4 · Review test results</div>
        <h1>{header}</h1>
        <div>
          {groups.map((g) => (
            <div className="grp" key={g.category}>
              <div className="gt">{g.category}</div>
              {g.scenarios.map((s, i) => {
                const passed =
                  s.safe && (s.useful || s.note.indexOf("refused") >= 0);
                return (
                  <div className={"res" + (passed ? "" : " fail")} key={i}>
                    <span className={"mk " + (passed ? "p" : "f")}>
                      {passed ? "Passed" : "Failed"}
                    </span>
                    <span style={{ marginLeft: 10 }}>{s.note || s.name}</span>
                  </div>
                );
              })}
            </div>
          ))}
        </div>
        <div className="nav2">
          <NavLink to="check" className="lk">
            ← Back
          </NavLink>
          <NavLink to="certificate" className="btn">
            See certificate
          </NavLink>
        </div>
      </div>
    </Screen>
  );
}
