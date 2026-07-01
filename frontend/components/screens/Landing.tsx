"use client";

import { Screen } from "@/components/Screen";
import { NavLink, ScreenId } from "@/lib/nav";

export function Landing({ active }: { active: ScreenId }) {
  return (
    <Screen id="landing" active={active} brand="reg-brand">
      <div className="hero">
        <div className="wrap hgrid">
          <div>
            <span className="eyebrow">Agent assurance for government</span>
            <h1>
              Move government AI agents from pilot to supervised live service.
            </h1>
            <p className="lede">
              SwarmSight checks the agent, the source documents, the policy, and
              the action before anything reaches a citizen, a record, or a
              system.
            </p>
            <div className="ctas">
              <NavLink to="home" className="btn ox">
                Enter SwarmSight →
              </NavLink>
              <NavLink to="officer" className="btn out">
                See the supervised workflow
              </NavLink>
            </div>
            <div className="micro">
              <span className="dot" />
              Nothing is sent without an officer.
            </div>
          </div>
          <div className="gate">
            <div className="gt">
              <span className="a">Housing appeals agent v3</span>
              <span className="flow">
                sandbox <b>→ supervised live</b>
              </span>
            </div>
            <div className="checks">
              <div className="ck ok">
                <span className="ic">✓</span>
                <span className="lab">Agent certificate</span>
                <span className="val">passed, L2</span>
              </div>
              <div className="ck warn">
                <span className="ic">!</span>
                <span className="lab">Source documents</span>
                <span className="val">78%, below threshold</span>
              </div>
              <div className="ck ok">
                <span className="ic">✓</span>
                <span className="lab">Policy bound</span>
                <span className="val">HA-09 v7</span>
              </div>
              <div className="ck ok">
                <span className="ic">✓</span>
                <span className="lab">Human judgement rule</span>
                <span className="val">active</span>
              </div>
            </div>
            <div className="verdict">
              <span className="stamp">Promotion held</span>
              <span className="vt">
                <b>Sources below the readiness threshold.</b> Clear the sources,
                or promote for L2 actions only.
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="sect">
        <div className="wrap">
          <span className="eyebrow">How it works</span>
          <h2 className="big">
            Check before go-live. Control during work. Prove afterwards.
          </h2>
          <div className="loop">
            <div className="lc">
              <div className="n">01</div>
              <h3>Test the agent</h3>
              <p>
                Run it against realistic cases in an arena. It earns a
                certificate that says exactly what it may do.
              </p>
            </div>
            <div className="lc">
              <div className="n">02</div>
              <h3>Certify the sources</h3>
              <p>
                Check documents are current and permissioned. Unsafe sources are
                blocked before an agent can cite them.
              </p>
            </div>
            <div className="lc">
              <div className="n">03</div>
              <h3>Control the action</h3>
              <p>
                Every action returns one plain verdict: allowed, masked, held
                for an officer, or blocked.
              </p>
            </div>
            <div className="lc">
              <div className="n">04</div>
              <h3>Prove the decision</h3>
              <p>
                Source, policy version, the draft, the human edit, and the
                approver are sealed in a verifiable trail.
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="sect">
        <div className="wrap">
          <span className="eyebrow">One scenario</span>
          <h2 className="big">
            A housing appeal, prepared and held at the right line.
          </h2>
          <div className="scenario">
            <div className="sl">
              Housing appeals agent v3, in live supervised service
            </div>
            <h3>
              The agent prepares the response. SwarmSight catches the missing
              eviction evidence and holds the final send for an officer.
            </h3>
            <div className="sflow">
              <div className="sstep">
                <div className="k">Prepares</div>
                <div className="v">
                  Reads the file, drafts a response, sets out the evidence.
                </div>
              </div>
              <div className="sstep">
                <div className="k">Catches</div>
                <div className="v">
                  Eviction risk with dependent children, eviction notice
                  missing.
                </div>
              </div>
              <div className="sstep stop">
                <div className="k">Holds</div>
                <div className="v">
                  Final send is stopped. Sent to an officer with the evidence.
                </div>
              </div>
              <div className="sstep">
                <div className="k">Decides</div>
                <div className="v">
                  The officer edits, approves, and sends. Recorded.
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="sect final">
        <div className="wrap">
          <span className="eyebrow" style={{ color: "#E9A79F" }}>
            Get started
          </span>
          <h2 className="big">
            Start in shadow mode. Prove the safe slice. Turn on only the low-risk
            parts.
          </h2>
          <p>Keep humans in charge of judgement, and prove every step.</p>
          <div className="ctas" style={{ marginTop: 24 }}>
            <NavLink to="home" className="btn light">
              Enter SwarmSight →
            </NavLink>
            <NavLink to="oversight" className="btn ghost">
              Monitor a live agent
            </NavLink>
          </div>
        </div>
      </div>
    </Screen>
  );
}
