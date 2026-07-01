"use client";

import { useCallback, useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { ScreenId } from "@/lib/nav";
import { authority } from "@/lib/api";

interface Health {
  mode: string;
  graph: string;
  extraction: string;
  token?: string;
  siteId?: string;
  documentLibraryFiles?: number;
  ok: boolean;
  error?: string;
}
interface ProofPack {
  chainVerification?: { ok: boolean; checkedRows: number; message?: string };
}

// A live "receipts" page: everything here is read from the backend on demand, so
// it is proof the demo is genuinely connected, not fixed front-end text. The
// SharePoint site id and document version come from Microsoft Graph; the ledger
// verification recomputes every hash in the chain.
export function Status({ active }: { active: ScreenId }) {
  const [health, setHealth] = useState<Health | null>(null);
  const [chain, setChain] = useState<ProofPack["chainVerification"] | null>(null);
  const [checkedAt, setCheckedAt] = useState<string>("");
  const [busy, setBusy] = useState(false);

  const recheck = useCallback(() => {
    setBusy(true);
    Promise.allSettled([
      authority.get<Health>("/sources/sharepoint/health").then(setHealth),
      authority
        .get<ProofPack>("/cases/HX-4471/proof-pack")
        .then((p) => setChain(p.chainVerification ?? null)),
    ]).finally(() => {
      setCheckedAt(new Date().toLocaleTimeString("en-GB"));
      setBusy(false);
    });
  }, []);

  useEffect(() => {
    recheck();
  }, [recheck]);

  const spHost = health?.siteId ? health.siteId.split(",")[0] : "";
  const spLive = health?.graph === "live" && health?.ok;
  const claudeLive = health?.extraction === "claude";

  function Row({
    label,
    ok,
    children,
  }: {
    label: string;
    ok: boolean;
    children: React.ReactNode;
  }) {
    return (
      <div className="ctl-row">
        <span
          style={{
            fontWeight: 700,
            color: ok ? "#00703c" : "#d4351c",
            width: 22,
          }}
        >
          {ok ? "✓" : "•"}
        </span>
        <span className="ctl-main">
          <span className="nm">{label}</span>
          <span className="dt">{children}</span>
        </span>
      </div>
    );
  }

  return (
    <Screen id="status" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Live status</span>
        </span>
      </div>
      <div className="gp">
        <span className="tag t-b">Live</span>
        <span>Read from the backend right now. Re-check to see it refresh.</span>
      </div>
      <div className="mn">
        <h1>Is this really live?</h1>
        <p className="lead">
          Every value below is fetched from the backend on demand, not written
          into the page. The SharePoint site id and document version come from
          Microsoft Graph; the ledger check recomputes every hash in the chain.
        </p>

        <div className="ctl-panel" style={{ borderTopColor: "#1d70b8" }}>
          <h2>Connections</h2>
          <Row label="Department documents (SharePoint, Microsoft Graph)" ok={!!spLive}>
            {health
              ? spLive
                ? `Live. Connected to ${spHost || "the department site"}, ${
                    health.documentLibraryFiles ?? 0
                  } document(s), app-only token ${health.token || "ok"}.`
                : `Not live (${health.error || "using the in-process mock"}).`
              : "Checking…"}
          </Row>
          <Row label="Field extraction (Anthropic Claude)" ok={!!claudeLive}>
            {health
              ? claudeLive
                ? "Live. Reading each application document with Claude."
                : "Offline fallback (no key set)."
              : "Checking…"}
          </Row>
        </div>

        <div className="ctl-panel" style={{ borderTopColor: "#0b0c0c" }}>
          <h2>Tamper-proof record (ledger)</h2>
          <Row label="Hash-chained ledger" ok={!!chain?.ok}>
            {chain
              ? `${chain.checkedRows} rows in the chain, re-verified: ${
                  chain.ok
                    ? "intact, every hash recomputed just now"
                    : `TAMPER DETECTED, ${chain.message || "broken"}`
                }.`
              : "Checking…"}
          </Row>
          <div className="dt" style={{ marginTop: 8, color: "#505a5f" }}>
            Every decision, document fetch, edit and approval is a row whose hash
            depends on the one before it. Change any past row and the chain
            fails, so the record cannot be quietly edited.
          </div>
        </div>

        <div style={{ marginTop: 16 }}>
          <span
            className="gov-btn"
            role="button"
            onClick={busy ? undefined : recheck}
          >
            {busy ? "Re-checking…" : "Re-check now"}
          </span>
          {checkedAt && (
            <span style={{ marginLeft: 12, fontSize: 13, color: "#505a5f" }}>
              Last checked {checkedAt} (watch this time change).
            </span>
          )}
        </div>

        <div className="note" style={{ marginTop: 18 }}>
          <b>To prove it live in the demo:</b> edit the applicant&apos;s file in
          SharePoint, then re-open Sensitive data — the document version changes
          and the new value flows through. Or paste any legislation.gov.uk URL on
          the policy screen and watch Claude extract the rules. Unpredictable
          input, real response.
        </div>
      </div>
    </Screen>
  );
}
