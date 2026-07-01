"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, SourceReadiness } from "@/lib/api";

export function Sources({ active }: { active: ScreenId }) {
  const [sources, setSources] = useState<SourceReadiness[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authority
      .get<SourceReadiness[]>("/sources/readiness")
      .then(setSources)
      .catch((err) => setError(err.message));
  }, []);

  return (
    <Screen id="sources" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Agent assurance</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>
          The gate reads source readiness before allowing citizen-facing
          promotion.
        </span>
      </div>
      <div className="mn">
        <div className="crumb">
          <NavLink to="gate" className="lk">
            Go-live check
          </NavLink>{" "}
          › Source readiness
        </div>
        <h1>Source readiness</h1>
        <p className="lead">
          Each source is scored against a readiness threshold. A source below its
          threshold blocks citizen-facing promotion. These snapshots are the ones
          Authority holds.
        </p>
        <div style={{ marginTop: 18 }}>
          {error ? (
            "Could not reach Authority (" + error + ")."
          ) : !sources ? (
            "Loading source readiness from Authority…"
          ) : (
            sources.map((s, i) => {
              const ready = s.score >= s.threshold;
              return (
                <div className="src" key={i}>
                  <span>
                    <b>{s.sourceId}</b>{" "}
                    <span className="meta">{s.connector}</span> · {s.score}%
                    against {s.threshold}% threshold
                    {s.flags && s.flags.length
                      ? " · flags: " + s.flags.join(", ")
                      : ""}
                  </span>
                  {ready ? (
                    <span className="pill p-strong">ready</span>
                  ) : (
                    <span className="pill p-missing">below threshold</span>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </Screen>
  );
}
