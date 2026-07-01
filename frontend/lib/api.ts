// Thin client for the Authority and Intelligence services. The browser calls
// these directly, exactly as the original connected demo did, so the verdict,
// certificate, gate, oversight, and ledger data on screen is always live.

// Default to same-origin proxy prefixes (see rewrites in next.config.js) so the
// browser never makes a cross-origin call: it hits this Next.js origin and the
// server forwards to the backend. Set NEXT_PUBLIC_* to a full URL only if you
// deliberately want the browser to call the services directly.
export const AUTHORITY_API =
  process.env.NEXT_PUBLIC_AUTHORITY_API || "/_authority";
export const INTELLIGENCE_API =
  process.env.NEXT_PUBLIC_INTELLIGENCE_API || "/_intelligence";

export const CASE_REF = "HX-4471";
export const AGENT_ID = "housing-appeals-agent-v3";

// A 401 means the session is missing or expired. In the browser, send the user
// to the login page rather than surfacing a raw error on a data screen.
function handleUnauthorized(status: number) {
  if (status === 401 && typeof window !== "undefined") {
    window.location.href = "/login";
  }
}

async function getJson<T>(base: string, path: string): Promise<T> {
  const r = await fetch(base + path);
  if (!r.ok) {
    handleUnauthorized(r.status);
    throw new Error("HTTP " + r.status);
  }
  return r.json() as Promise<T>;
}

async function postJson<T>(
  base: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const r = await fetch(base + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    handleUnauthorized(r.status);
    throw new Error("HTTP " + r.status);
  }
  return r.json() as Promise<T>;
}

export const authority = {
  get: <T>(path: string) => getJson<T>(AUTHORITY_API, path),
  post: <T>(path: string, body?: unknown) => postJson<T>(AUTHORITY_API, path, body),
};

export const intelligence = {
  get: <T>(path: string) => getJson<T>(INTELLIGENCE_API, path),
  post: <T>(path: string, body?: unknown) =>
    postJson<T>(INTELLIGENCE_API, path, body),
};

// ---- shared formatting helpers (ported from the demo's inline JS) ----

export function ukDate(value: string | number | Date): string {
  return new Date(value).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export function ukDateTime(value: string | number | Date): string {
  return new Date(value).toLocaleString("en-GB");
}

export function shortHash(h: unknown): string {
  return String(h).slice(0, 12) + "…";
}

// ---- response shapes (only the fields the screens read) ----

export interface Verdict {
  effect: "ALLOW" | "HOLD" | "BLOCK";
  reasonCode: string;
  reviewBrief: string;
  policyVersion: string;
  runId: string;
  caseRef: string;
  action: string;
  seq: number;
  rowHash: string;
}

export interface Guard {
  name: string;
  raiseTo: string;
  source: string;
}

export interface PolicyVersion {
  policyId: string;
  version: string;
  effectiveFrom: string;
  status?: "ACTIVE" | "SCHEDULED" | "SUPERSEDED" | string;
  actions?: string[];
  requiredInputs?: string[];
  guards?: Guard[];
}

export interface IngestionResult {
  changeId: string;
  policyId: string;
  baseVersion: string;
  proposedVersion: string;
  sourceUri: string;
  contentHash: string;
  title: string;
  fetchedChars: number;
  addedGuards: number;
  commencementDate?: string | null;
  suggestedEffectiveFrom: string;
  extraction: "claude" | "fallback" | string;
  summary?: string;
}

export interface PolicyChange {
  id: string;
  status: "PROPOSED" | "HELD" | "ACTIVATED" | string;
  policyId: string;
  baseVersion: string;
  proposedVersion: string;
  conflictReason?: string;
  sources: { title?: string }[];
  suggestedEffectiveFrom?: string | null;
}

export interface ShadowReplayResult {
  caseRef: string;
  oldEffect: string;
  newEffect: string;
  changed: boolean;
}

export interface PolicyChangeView {
  change: PolicyChange;
  diff: { addedGuards?: Guard[] };
  shadowReport?: {
    changedCount: number;
    totalCount: number;
    results?: ShadowReplayResult[];
  };
}

export interface ArenaScenario {
  category: string;
  name: string;
  note: string;
  safe: boolean;
  useful: boolean;
}

export interface Certificate {
  id: string;
  issuedAt: string;
  expiresAt: string;
  approver: string;
  builder: string;
  status: string;
  ceiling: string;
  certifiedActions: string[];
  notCertifiedActions: string[];
}

export interface CertificateReport {
  arenaSummary: { results: ArenaScenario[] };
  certificate: Certificate;
}

export interface GoLive {
  certificate: { id?: string; present: boolean; expired: boolean };
  policyBound: boolean;
  policyVersion?: string;
  sources: { score: number }[];
  sourcesReady: boolean;
  connectorsHealthy: boolean;
  humanJudgementActive: boolean;
  ceilingOk: boolean;
  requestedCeiling: string;
  certifiedCeiling: string;
  promotable: boolean;
  verdict: string;
  blockers: string[];
}

export interface SourceReadiness {
  sourceId: string;
  connector: string;
  score: number;
  threshold: number;
  flags?: string[];
}

export interface ProofPack {
  chainVerification: { ok: boolean; message: string };
  whatHappened: { narrative: string };
  whyReview: { reasonCode: string; brief: string; guardsFired?: Guard[] };
  responsibility: {
    authoredBy: string;
    finalWordingBy: string;
    approvedBy: string;
    policy: string;
  };
  decisionTrace: { intent: string; label: string; actor: string; rowHash: string }[];
  humanJudgement: {
    diff?: { op: "EQUAL" | "DELETE" | "INSERT"; text: string }[];
    note?: string;
    appealRoute?: string;
    signposting?: string;
  };
  sources: { provided?: string[]; missing?: string[] };
  governingRules: {
    policy: string;
    officer: string;
    decidedAt: string;
    technical: {
      ledgerEntries: number;
      draftHash: string;
      editHash: string;
      approveHash: string;
      exportHash: string;
    };
  };
}

export type OversightMetrics = Record<string, number>;

export interface Incident {
  trigger: string;
  status: string;
  agentId: string;
  detail?: string;
  containment: {
    suspendedCertificate?: string;
    revokedCapabilities?: unknown[];
    heldCases?: unknown[];
  };
}

export interface LedgerRow {
  seq: number;
  intent: string;
  caseRef: string;
  actor: string;
  rowHash: string;
}

export interface ScenarioResult {
  scenarioId: string;
  name: string;
  category: string;
  severity: string;
  proposedAction: string;
  verdictEffect: string;
  safe: boolean;
  useful: boolean;
  note: string;
}

export interface ArenaRun {
  safetyPass: boolean;
  usefulnessScore: number;
  recommendedCeiling: string;
  overallPass?: boolean;
  certifiedActions?: string[];
  notCertifiedActions?: string[];
  results?: ScenarioResult[];
}

export interface CapabilityProposal {
  action: string;
  label: string;
  citizenFacing: boolean;
  recommendedAllow: boolean;
  rationale: string;
}
