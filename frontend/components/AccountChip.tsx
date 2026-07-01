"use client";

import { useEffect, useState } from "react";
import { ROLE_LABELS, Role, useUser } from "@/lib/user";
import { useNav } from "@/lib/nav";

// A live connection indicator: pings the backend health endpoint on an interval
// and shows a pulsing green LIVE (or red OFFLINE), so the connection is visible
// on every screen without opening the status page.
function LiveDot() {
  const [live, setLive] = useState<boolean | null>(null);
  useEffect(() => {
    let alive = true;
    const ping = () =>
      fetch("/_authority/health")
        .then((r) => alive && setLive(r.ok))
        .catch(() => alive && setLive(false));
    ping();
    const id = setInterval(ping, 15000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);
  return (
    <span
      className={`live-pill ${live === false ? "down" : "on"}`}
      title="Live connection to the SwarmSight backend"
    >
      <span className="d" />
      {live === null ? "…" : live ? "LIVE" : "OFFLINE"}
    </span>
  );
}

const PERSONAS: { role: Role; email: string }[] = [
  { role: "OFFICER", email: "officer@swarmsight.local" },
  { role: "HEAD_OF_DEPARTMENT", email: "head@swarmsight.local" },
  { role: "SERVICE_OWNER", email: "owner@swarmsight.local" },
  { role: "ADMIN", email: "admin@swarmsight.local" },
];

// The top-right session control: who you are, a demo "View as" persona switcher
// (actually signs in as that demo account, so its real screens and controls
// show), quick links (home, story tour), and sign out.
export function AccountChip({ onStory }: { onStory: () => void }) {
  const { user } = useUser();
  const { go } = useNav();
  const [open, setOpen] = useState(false);
  const [switching, setSwitching] = useState(false);

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    window.location.href = "/";
  }

  // Real persona switch: sign in as the demo account, then reload to its desk.
  async function switchTo(email: string) {
    if (email === user?.email) {
      setOpen(false);
      go("home");
      return;
    }
    setSwitching(true);
    const r = await fetch("/api/auth/persona", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }),
    }).catch(() => null);
    if (r && r.ok) {
      window.location.href = "/app";
    } else {
      setSwitching(false);
      setOpen(false);
    }
  }

  if (!user) return null;

  return (
    <div
      style={{
        position: "fixed",
        top: 10,
        right: 12,
        zIndex: 400,
        fontFamily: "system-ui, sans-serif",
        display: "flex",
        alignItems: "center",
        gap: 8,
      }}
    >
      <LiveDot />
      <div style={{ position: "relative" }}>
      <button onClick={() => setOpen((o) => !o)} style={chipStyle}>
        <span style={{ fontWeight: 700 }}>{user.displayName}</span>
        <span style={{ color: "#b1b4b6" }}>· {ROLE_LABELS[user.role]}</span>
        <span style={{ color: "#b1b4b6" }}>▾</span>
      </button>
      {open && (
        <div style={menuStyle}>
          <div style={{ padding: "8px 12px", fontSize: 12, color: "#6b7177" }}>
            {user.email}
          </div>
          <button
            onClick={() => {
              setOpen(false);
              go("home");
            }}
            style={{ ...itemStyle, width: "100%" }}
          >
            My desk (home)
          </button>
          <button
            onClick={() => {
              setOpen(false);
              onStory();
            }}
            style={{ ...itemStyle, width: "100%" }}
          >
            Show me how it works
          </button>
          <button
            onClick={() => {
              setOpen(false);
              go("departments");
            }}
            style={{ ...itemStyle, width: "100%" }}
          >
            Boroughs
          </button>
          <button
            onClick={() => {
              setOpen(false);
              go("status");
            }}
            style={{ ...itemStyle, width: "100%" }}
          >
            Live status (is this real?)
          </button>

          <div className="persona-h">
            {switching ? "Switching…" : "View as (demo)"}
          </div>
          {PERSONAS.map((p) => (
            <button
              key={p.role}
              className={`persona-i${user.email === p.email ? " on" : ""}`}
              disabled={switching}
              onClick={() => switchTo(p.email)}
            >
              {user.email === p.email ? "● " : "○ "}
              {ROLE_LABELS[p.role]}
              {user.email === p.email ? " (you)" : ""}
            </button>
          ))}

          {user.role === "ADMIN" && (
            <a href="/admin" style={itemStyle}>
              Manage accounts
            </a>
          )}
          <button onClick={signOut} style={{ ...itemStyle, width: "100%" }}>
            Sign out
          </button>
        </div>
      )}
      </div>
    </div>
  );
}

const chipStyle: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  gap: 7,
  fontSize: 12.5,
  background: "#101216",
  color: "#e7e7e3",
  border: "1px solid #2c3036",
  borderRadius: 4,
  padding: "6px 11px",
  cursor: "pointer",
  fontFamily: "inherit",
};

const menuStyle: React.CSSProperties = {
  marginTop: 6,
  background: "#101216",
  border: "1px solid #2c3036",
  borderRadius: 4,
  minWidth: 180,
  overflow: "hidden",
  boxShadow: "0 8px 24px rgba(0,0,0,.4)",
};

const itemStyle: React.CSSProperties = {
  display: "block",
  textAlign: "left",
  padding: "9px 12px",
  fontSize: 13,
  color: "#e7e7e3",
  background: "transparent",
  border: "none",
  borderTop: "1px solid #2c3036",
  cursor: "pointer",
  textDecoration: "none",
  fontFamily: "inherit",
};
