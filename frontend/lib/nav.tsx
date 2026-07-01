"use client";

import { createContext, useContext } from "react";

// Every screen id the demo can show. Order here is not the nav order; the shell
// groups them explicitly below.
export type ScreenId =
  | "landing"
  | "home"
  | "status"
  | "departments"
  | "department"
  | "agents"
  | "check"
  | "results"
  | "certificate"
  | "gate"
  | "sources"
  | "officer"
  | "case"
  | "masking"
  | "policies"
  | "audit"
  | "oversight"
  | "agentlog";

export interface NavGroup {
  label: string;
  tabs: { id: ScreenId; label: string }[];
}

// The grouped tabs rendered in the top shell, matching the original demo's nav.
export const NAV_GROUPS: NavGroup[] = [
  {
    label: "Front door",
    tabs: [
      { id: "home", label: "My desk" },
      { id: "departments", label: "Departments" },
      { id: "landing", label: "Landing" },
    ],
  },
  {
    label: "Assurance",
    tabs: [
      { id: "check", label: "Check agent" },
      { id: "results", label: "Test results" },
      { id: "certificate", label: "Certificate" },
      { id: "gate", label: "Go-live check" },
    ],
  },
  {
    label: "The service",
    tabs: [
      { id: "officer", label: "Officer" },
      { id: "case", label: "Case" },
      { id: "masking", label: "Sensitive data" },
    ],
  },
  { label: "Audit", tabs: [{ id: "audit", label: "Proof pack" }] },
  {
    label: "Oversight",
    tabs: [
      { id: "oversight", label: "Head of department" },
      { id: "agentlog", label: "Per-agent log" },
    ],
  },
];

interface NavContextValue {
  active: ScreenId;
  go: (id: ScreenId) => void;
}

export const NavContext = createContext<NavContextValue>({
  active: "landing",
  go: () => {},
});

export function useNav() {
  return useContext(NavContext);
}

// Drop-in for the original's `data-t` links: a span/button that navigates on
// click. Renders inert (`as`) elements so existing class names still apply.
export function NavLink({
  to,
  className,
  style,
  children,
}: {
  to: ScreenId;
  className?: string;
  style?: React.CSSProperties;
  children: React.ReactNode;
}) {
  const { go } = useNav();
  return (
    <span
      className={className}
      style={style}
      onClick={() => go(to)}
      role="button"
    >
      {children}
    </span>
  );
}
