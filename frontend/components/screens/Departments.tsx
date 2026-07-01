"use client";

import { useState } from "react";
import { Screen } from "@/components/Screen";
import { ScreenId, useNav } from "@/lib/nav";
import { useUser, ROLE_LABELS } from "@/lib/user";

// Housing is the live department; the others share the identical governed flow
// but are not stood up in this demo. The cards are the front door to the whole
// system: one click opens a department's control tower.
const OTHERS = [
  { key: "d-benefits", name: "London Borough of Camden", btn: "g-green", label: "Open Camden" },
  { key: "d-planning", name: "London Borough of Hackney", btn: "g-purple", label: "Open Hackney" },
  { key: "d-foi", name: "City of Westminster", btn: "g-red", label: "Open Westminster" },
];

export function Departments({ active }: { active: ScreenId }) {
  const { go } = useNav();
  const { user } = useUser();
  const [note, setNote] = useState<string | null>(null);

  return (
    <Screen id="departments" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Boroughs</span>
        </span>
      </div>
      <div className="mn">
        <h1>
          {user ? user.displayName : "Welcome"}
          {user && (
            <span style={{ color: "#505a5f", fontWeight: 400 }}>
              {" "}
              · {ROLE_LABELS[user.role]}
            </span>
          )}
        </h1>
        <p className="lead">
          Pick a borough to see how its housing service is running, the
          workflows, the agents, and where a person decides.
        </p>

        <div className="dept-grid">
          <div
            className="dept-card clickable"
            role="button"
            tabIndex={0}
            onClick={() => go("department")}
            onKeyDown={(e) => e.key === "Enter" && go("department")}
          >
            <div className="dept-top">
              <span className="dept-name">Kensington &amp; Chelsea</span>
              <span className="dept-st live">LIVE</span>
              <span className="dept-chev">›</span>
            </div>
            <p className="dept-desc">
              Royal Borough of Kensington and Chelsea. Housing service: appeals,
              homelessness, and FOI triage, running under assurance.
            </p>
            <span
              className="gov-btn"
              role="button"
              onClick={(e) => {
                e.stopPropagation();
                go("department");
              }}
            >
              Open Kensington &amp; Chelsea
            </span>
          </div>

          {OTHERS.map((d) => (
            <div className={`dept-card ${d.key}`} key={d.key}>
              <div className="dept-top">
                <span className="dept-name">{d.name}</span>
                <span className="dept-st avail">AVAILABLE</span>
                <span className="dept-chev">›</span>
              </div>
              <p className="dept-desc">
                Not yet stood up. Same governed flow as Kensington and Chelsea.
              </p>
              <span
                className={`gov-btn ${d.btn}`}
                role="button"
                onClick={() =>
                  setNote(
                    `${d.name} would run the identical governed flow as Kensington and Chelsea: register its agents and ingest the borough's own allocation policy to stand it up. Not enabled in this demo.`,
                  )
                }
              >
                {d.label}
              </span>
            </div>
          ))}
        </div>

        {note && (
          <div className="note" style={{ marginTop: 16 }}>
            {note}
          </div>
        )}

        <p style={{ fontSize: 14, color: "#505a5f", marginTop: 20 }}>
          <span className="human-mark" /> where a human decides · overview first,
          then the detail you ask for.
        </p>
      </div>
    </Screen>
  );
}
