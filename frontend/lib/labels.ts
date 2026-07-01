// Plain-English labels for the machine identifiers the backend uses (policy
// levels, action names, guard names, reason codes). The UI should read like
// normal English; the raw identifier is only ever a fallback. Use humanize() for
// anything, or the named helpers for a specific kind.

const KNOWN: Record<string, string> = {
  // Autonomy levels. L4_HUMAN is the important one: a person must decide.
  L0: "handled automatically",
  L1: "light-touch check",
  L2: "checked by the assistant",
  L3: "close human review",
  L4_HUMAN: "a person must decide",

  // Actions an assistant can take.
  draft_response: "Draft a response",
  request_evidence: "Request missing evidence",
  escalate: "Escalate to an officer",
  summarise_case: "Summarise the case",
  send_decision: "Send a decision to the citizen",
  close_case: "Close the case",
  release_records: "Release records",

  // Guard names and reason codes used in the housing demo.
  "eviction-risk-with-dependents": "Eviction risk with dependent children",
  "evidence-missing": "Key evidence is missing",
  "section-21-ground-removed": "Section 21 ‘no-fault’ ground removed",
  "section_21_abolition": "Section 21 ‘no-fault’ ground abolished",
  EVICTION_RISK_DEPENDENTS: "Eviction risk with dependent children",
  EVIDENCE_MISSING: "Key evidence is missing",
  S21_ABOLISHED: "Section 21 ‘no-fault’ ground abolished",

  // Verdict effects.
  ALLOW: "allowed",
  HOLD: "held for a person",
  BLOCK: "blocked",

  // Synthetic test cases used in the change preview.
  "syn-clear": "A straightforward case",
  "syn-eviction-dependents": "Eviction risk with children",
  "syn-section21": "A section 21 eviction case",
  "syn-evidence-missing": "A case with evidence missing",

  // Case input fields, in case they surface.
  tenancy_status: "Tenancy status",
  eviction_risk: "Eviction risk",
  dependent_children: "Dependent children",
  eviction_notice: "Eviction notice",
  section_21_ground: "Section 21 ground",
  adverse_outcome: "Adverse outcome",
};

/** Turn any backend identifier into readable English. Known ones are mapped; the
 *  rest have their snake_case / kebab-case / UPPER_CASE flattened to a sentence. */
export function humanize(id: string | null | undefined): string {
  if (!id) return "";
  const known = KNOWN[id];
  if (known) return known;
  const s = id.replace(/[_-]+/g, " ").trim();
  // Leave real prose (has spaces already and mixed case) mostly alone.
  const lower = s.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export const levelLabel = humanize;
export const actionLabel = humanize;
export const guardLabel = humanize;
export const reasonLabel = humanize;

// Who took a ledger action, derived from the action type: a person, the
// assistant, or the Authority (the governing engine). This makes the
// human-vs-assistant split explicit on the audit trail.
export type ActorKind = "Human" | "Assistant" | "Authority";

export function actorKind(intent?: string | null): ActorKind {
  switch (intent) {
    // A person: an officer's edits/approvals, or oversight/sign-off actions.
    case "edit":
    case "approve":
    case "incident_raised":
    case "certificate_suspended":
    case "action_class_disabled":
    case "case_held":
    case "service_owner_notified":
    case "policy_changed":
    case "certificate_issued":
      return "Human";
    // The assistant: preparing a draft, fetching documents through the broker.
    case "author":
    case "source_fetch":
      return "Assistant";
    // The Authority engine: the governed verdict, capabilities minted/revoked.
    default:
      return "Authority";
  }
}

// The autonomy ceiling in plain English: how much the assistant may do on its
// own. Sending a decision or closing a case always stays with a person.
const CEILING: Record<string, string> = {
  L0: "nothing on its own",
  L1: "prepare only",
  L2: "prepare & check only",
  L3: "act with oversight",
  L4_HUMAN: "person only",
};

export function ceilingLabel(level?: string | null): string {
  if (!level) return "";
  return CEILING[level] || humanize(level);
}

// A one-line explanation of the ceiling, for a tooltip or caption.
export const CEILING_EXPLAINER =
  "The assistant may draft, request evidence, and escalate on its own. Sending a decision to a citizen or closing a case always needs an officer.";
