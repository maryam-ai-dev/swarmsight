"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId, useNav } from "@/lib/nav";
import { authority } from "@/lib/api";
import { useCase } from "@/lib/caseref";

interface CaseItem {
  caseRef: string;
  name: string;
  version: string;
}

export function Officer({ active }: { active: ScreenId }) {
  const { go } = useNav();
  const { setCaseRef } = useCase();
  const [cases, setCases] = useState<CaseItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authority
      .get<CaseItem[]>("/cases")
      .then(setCases)
      .catch((e) => setError(e.message));
  }, []);

  function open(ref: string) {
    setCaseRef(ref);
    go("masking");
  }

  return (
    <Screen id="officer" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Housing appeals</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Beta</span>
        <span>
          This service is in a supervised trial. Nothing is sent without an
          officer.
        </span>
      </div>
      <div className="mn">
        <div className="cap">J. Okafor, your cases</div>
        <h1>Cases for review</h1>
        <p style={{ margin: "6px 0 0" }}>
          <NavLink to="oversight" className="lk">
            Head of department oversight →
          </NavLink>
        </p>
        <p className="lead">
          {error
            ? "Could not reach the caseload (" + error + ")."
            : cases
              ? `${cases.length} application${cases.length === 1 ? "" : "s"} are live from the borough's SharePoint, prepared and checked against policy.`
              : "Loading the caseload from the borough's SharePoint…"}
        </p>
        <div className="inset">
          <b>You make every decision that affects a citizen.</b> The service
          prepares evidence and drafts and checks them against policy. It cannot
          send a decision, close a case, or release records on its own.
        </div>

        <div className="h2">
          For review{" "}
          <span className="c">
            {cases ? `${cases.length} live` : "…"}
          </span>
        </div>
        {cases &&
          cases.map((c) => (
            <div className="item" key={c.caseRef}>
              <div className="itop">
                <span className="lk" role="button" onClick={() => open(c.caseRef)}>
                  {c.caseRef}, housing application
                </span>
                <span className="tag t-y">For review</span>
              </div>
              <div className="meta">
                {c.name} · version{" "}
                <span style={{ fontFamily: "ui-monospace, monospace" }}>
                  {c.version.slice(0, 16)}
                </span>
              </div>
              <p className="src">
                Fetched live from the department&apos;s SharePoint. Open to see
                the masked record the assistant received.
              </p>
              <span className="btn" role="button" onClick={() => open(c.caseRef)}>
                Open case
              </span>
            </div>
          ))}
        {cases && cases.length === 0 && (
          <div className="clear">
            <div className="t">No applications in the library yet.</div>
            <div className="d">
              Drop application documents into the borough&apos;s SharePoint and
              they appear here as cases.
            </div>
          </div>
        )}
      </div>
    </Screen>
  );
}
