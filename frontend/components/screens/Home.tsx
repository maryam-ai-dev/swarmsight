"use client";

import { useEffect, useState } from "react";
import { Screen } from "@/components/Screen";
import { ScreenId, useNav } from "@/lib/nav";
import { useUser, Role, ROLE_LABELS } from "@/lib/user";
import { authority } from "@/lib/api";

// Each role's "desk": one plain job sentence, the one thing that needs them, and
// a single primary action. Recognition over recall; one clear next step.
interface Desk {
  job: string;
  headline: (m: Record<string, number>, people: number) => string;
  primary: { label: string; screen: ScreenId | "/admin" };
  secondary: { label: string; screen: ScreenId }[];
}

const DESKS: Record<Role, Desk> = {
  OFFICER: {
    job: "You make the decisions. The assistant prepares the work and checks it against the rules, it can never act on a citizen by itself.",
    headline: () => "3 cases are ready for your decision",
    primary: { label: "Open my caseload", screen: "officer" },
    secondary: [{ label: "Walk through one case", screen: "case" }],
  },
  HEAD_OF_DEPARTMENT: {
    job: "You keep the assistants in check. You can pause or restrict any of them at any time, and everything they do is watched.",
    headline: (m) =>
      `${m.active_certificates ?? 1} assistant running · ${m.incidents ?? 0} problems to review`,
    primary: { label: "Open oversight", screen: "oversight" },
    secondary: [{ label: "See the whole department", screen: "department" }],
  },
  SERVICE_OWNER: {
    job: "You decide what is allowed to go live. Nothing reaches the public until you have tested it and signed it off.",
    headline: () => "1 assistant is waiting for your go-live sign-off",
    primary: { label: "Run the safety test", screen: "check" },
    secondary: [{ label: "Go-live checklist", screen: "gate" }],
  },
  ADMIN: {
    job: "You manage the people and what each of them is allowed to do.",
    headline: (_m, people) => `${people} people across 4 roles`,
    primary: { label: "Manage accounts", screen: "/admin" },
    secondary: [{ label: "See the whole department", screen: "department" }],
  },
};

export function Home({
  active,
  onStory,
}: {
  active: ScreenId;
  onStory: () => void;
}) {
  const { go } = useNav();
  const { user, effectiveRole } = useUser();
  const [m, setM] = useState<Record<string, number>>({});
  const [people, setPeople] = useState(0);

  useEffect(() => {
    authority.get<Record<string, number>>("/oversight/metrics").then(setM).catch(() => {});
    authority
      .get<unknown[]>("/auth/directory")
      .then((d) => setPeople(d.length))
      .catch(() => {});
  }, []);

  const role = effectiveRole;
  const desk = role ? DESKS[role] : null;

  function open(screen: ScreenId | "/admin") {
    if (screen === "/admin") window.location.href = "/admin";
    else go(screen);
  }

  return (
    <Screen id="home" active={active} brand="reg-gov">
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Kensington &amp; Chelsea</span>
        </span>
      </div>
      <div className="mn">
        <h1>
          Good morning{user ? `, ${user.displayName.split(" ").slice(-1)[0]}` : ""}
        </h1>
        {desk && role ? (
          <>
            <div className="ctl-panel" style={{ borderTopColor: "#1d70b8" }}>
              <span className={`ctl-role ${roleClass(role)}`}>
                {ROLE_LABELS[role]}
              </span>
              <div style={{ fontSize: 15, color: "#0b0c0c", margin: "12px 0 4px", maxWidth: "44em" }}>
                {desk.job}
              </div>
              <div style={{ fontSize: 26, fontWeight: 700, color: "#0b0c0c", margin: "16px 0 14px" }}>
                {desk.headline(m, people)}
              </div>
              <span className="gov-btn" role="button" onClick={() => open(desk.primary.screen)}>
                {desk.primary.label} →
              </span>
              {desk.secondary.map((s) => (
                <button
                  key={s.screen}
                  className="ctl-open"
                  style={{ marginLeft: 16 }}
                  onClick={() => open(s.screen)}
                >
                  {s.label} →
                </button>
              ))}
            </div>

            <div
              className="ctl-panel"
              style={{ borderTopColor: "#ffdd00", background: "#fffbe6" }}
            >
              <h2 style={{ margin: 0 }}>New here? See how it works in 5 steps</h2>
              <div className="sub" style={{ marginTop: 4 }}>
                Follow one real housing case from start to finish, no jargon.
              </div>
              <span className="gov-btn g-green" role="button" onClick={onStory}>
                Show me how it works →
              </span>
            </div>

            <p style={{ fontSize: 14, color: "#505a5f", marginTop: 18 }}>
              <span className="human-mark" /> A person makes every decision that
              affects a citizen. The assistant only ever prepares and checks.
            </p>
          </>
        ) : (
          <p className="lead">Loading your desk…</p>
        )}
      </div>
    </Screen>
  );
}

function roleClass(role: Role): string {
  return role === "HEAD_OF_DEPARTMENT"
    ? "r-head"
    : role === "SERVICE_OWNER"
      ? "r-owner"
      : role === "ADMIN"
        ? "r-admin"
        : "";
}
