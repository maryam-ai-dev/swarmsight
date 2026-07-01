"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";
import { authority, CASE_REF, ProofPack as ProofPackData, shortHash, ukDateTime } from "@/lib/api";
import { humanize, actorKind } from "@/lib/labels";

export function ProofPack({ active }: { active: ScreenId }) {
  const [pack, setPack] = useState<ProofPackData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authority
      .get<ProofPackData>(`/cases/${encodeURIComponent(CASE_REF)}/proof-pack`)
      .then(setPack)
      .catch((err) => setError(err.message));
  }, []);

  const cv = pack?.chainVerification;

  return (
    <Screen id="audit" active={active} brand="reg-pp">
      <div className="wrap">
        <div
          className="verify"
          style={cv && !cv.ok ? { background: "#f8d7da" } : undefined}
        >
          <span className="m">✓</span>
          <div>
            <div className="t">
              {cv ? (cv.ok ? "Record verified" : "Chain break detected") : "Record verified"}
            </div>
            <div className="d">
              {error
                ? "Could not reach Authority (" + error + ")."
                : cv
                  ? cv.message
                  : "Verifying the audit chain with Authority…"}
            </div>
          </div>
        </div>
        <div className="head">
          <div>
            <div className="kick">Proof pack</div>
            <h1>Housing appeal HX-4471</h1>
          </div>
          <div className="ref">SWS-CERT-HX-V3-0047</div>
        </div>

        <div className="sec">
          <div className="sn">1 · What happened</div>
          <p className="body">
            {pack ? pack.whatHappened.narrative : "Loading from the ledger…"}
          </p>
        </div>

        <div className="sec">
          <div className="sn">2 · Why officer review was required</div>
          <div className="why">
            {pack ? (
              <>
                <b>{humanize(pack.whyReview.reasonCode)}</b> ·{" "}
                {pack.whyReview.brief}
                {(pack.whyReview.guardsFired || []).map((g, i) => (
                  <div style={{ marginTop: 6 }} key={i}>
                    · <b>{humanize(g.name)}</b> → {humanize(g.raiseTo)}{" "}
                    <span style={{ color: "#6f6f69" }}>({g.source})</span>
                  </div>
                ))}
              </>
            ) : (
              "Loading…"
            )}
          </div>
        </div>

        <div className="sec">
          <div className="sn">3 · Decision responsibility</div>
          <table>
            <tbody>
              <tr>
                <th>Item</th>
                <th>Owner</th>
              </tr>
              {pack && (
                <>
                  <RespRow k="Draft authored" v={pack.responsibility.authoredBy} />
                  <RespRow
                    k="Final wording"
                    v={pack.responsibility.finalWordingBy}
                  />
                  <RespRow
                    k="Decision approved"
                    v={pack.responsibility.approvedBy}
                  />
                  <RespRow k="Policy applied" v={pack.responsibility.policy} />
                </>
              )}
            </tbody>
          </table>
        </div>

        <div className="sec">
          <div className="sn">4 · Decision trace</div>
          <div className="trace">
            {pack
              ? (pack.decisionTrace || []).map((t, i) => {
                  const dot =
                    t.intent === "author" ? "A" : t.intent === "edit" ? "✎" : "✓";
                  const cls =
                    t.intent === "author"
                      ? "svc"
                      : t.intent === "edit"
                        ? "edit"
                        : "appr";
                  return (
                    <div className={"tr " + cls} key={i}>
                      <div className="rail">
                        <div className="dot">{dot}</div>
                        <div className="line" />
                      </div>
                      <div className="c">
                        <div className="ct">{t.label}</div>
                        <div className="cm">
                          <span
                            className={"who k-" + actorKind(t.intent).toLowerCase()}
                          >
                            {actorKind(t.intent)}
                          </span>{" "}
                          {t.actor} · row {shortHash(t.rowHash)}
                        </div>
                      </div>
                    </div>
                  );
                })
              : "Loading…"}
          </div>
        </div>

        <div className="sec">
          <div className="sn">5 · Where human judgement entered</div>
          <div className="diff">
            <div className="dh">Agent draft → officer final</div>
            <div className="db">
              {pack
                ? (pack.humanJudgement.diff || []).map((s, i) => {
                    if (s.op === "EQUAL") return <span key={i}>{s.text} </span>;
                    if (s.op === "DELETE")
                      return (
                        <span className="del" key={i}>
                          {s.text}{" "}
                        </span>
                      );
                    return (
                      <span className="ins" key={i}>
                        {s.text}{" "}
                      </span>
                    );
                  })
                : "Loading…"}
            </div>
          </div>
          <div className="diffnote">
            {pack
              ? (pack.humanJudgement.note || "") +
                (pack.humanJudgement.appealRoute
                  ? " Appeal route: " + pack.humanJudgement.appealRoute + "."
                  : "") +
                (pack.humanJudgement.signposting
                  ? " " + pack.humanJudgement.signposting
                  : "")
              : ""}
          </div>
        </div>

        <div className="sec">
          <div className="sn">6 · Sources and access</div>
          <div>
            {pack ? (
              <>
                {(pack.sources.provided || []).map((s, i) => (
                  <div className="src" key={"p" + i}>
                    <span>{s}</span>
                    <span className="pill p-strong">provided</span>
                  </div>
                ))}
                {(pack.sources.missing || []).map((s, i) => (
                  <div className="src" key={"m" + i}>
                    <span>
                      {s} <span className="meta">required</span>
                    </span>
                    <span className="pill p-missing">missing</span>
                  </div>
                ))}
              </>
            ) : (
              "Loading…"
            )}
          </div>
          <div className="withheld">
            <b>Withheld from the service:</b> National Insurance number{" "}
            <span className="pill p-mask">masked</span> &nbsp; medical notes{" "}
            <span className="pill p-deny">no access</span>
            <br />
            <span style={{ color: "#6f6f69" }}>
              Reason: not required for this draft. Masking is enforced at the
              broker boundary: the source is fetched under a capability and the
              permission mirror masks before the record reaches the agent.
            </span>{" "}
            &nbsp;
            <NavLink to="masking" className="lk">
              See what the agent could access
            </NavLink>
          </div>
        </div>

        <div className="sec">
          <div className="sn">7 · Governing rules</div>
          <div className="gov">
            {pack && (
              <>
                <span>
                  <span className="k">Policy</span>{" "}
                  <span className="v">{pack.governingRules.policy}</span>
                </span>
                <span>
                  <span className="k">Officer</span>{" "}
                  <span className="v">{pack.governingRules.officer}</span>
                </span>
                <span>
                  <span className="k">Decided</span>{" "}
                  <span className="v">
                    {ukDateTime(pack.governingRules.decidedAt)}
                  </span>
                </span>
              </>
            )}
          </div>
          <details>
            <summary>Show technical verification</summary>
            <div className="inner">
              {pack && cv ? (
                <>
                  ledger entries: {pack.governingRules.technical.ledgerEntries} ·
                  chain {cv.ok ? "intact, no gaps" : "BROKEN"}
                  <br />
                  draft hash: {pack.governingRules.technical.draftHash}…
                  <br />
                  edit hash: {pack.governingRules.technical.editHash}…
                  <br />
                  approve hash: {pack.governingRules.technical.approveHash}…
                  <br />
                  export hash: {pack.governingRules.technical.exportHash}
                </>
              ) : (
                "Loading…"
              )}
            </div>
          </details>
        </div>

        <div className="wow">
          This record proves the service prepared the work, but the officer made
          the decision.
        </div>
        <div className="acts">
          <button className="btn">Export proof pack</button>
          <button className="btn sec">Copy FOI summary</button>
          <NavLink to="case" className="lk" style={{ marginLeft: "auto" }}>
            ← Back to case
          </NavLink>
        </div>
      </div>
    </Screen>
  );
}

function RespRow({ k, v }: { k: string; v: string }) {
  return (
    <tr>
      <td>{k}</td>
      <td>
        <span className="owner">{v}</span>
      </td>
    </tr>
  );
}
