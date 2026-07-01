"use client";

import { useCallback, useEffect, useState } from "react";
import { Role, ROLE_LABELS } from "@/lib/user";

interface UserView {
  id: string;
  email: string;
  role: Role;
  displayName: string;
  active: boolean;
}

const ROLES: Role[] = ["OFFICER", "HEAD_OF_DEPARTMENT", "SERVICE_OWNER", "ADMIN"];

export default function AdminPage() {
  const [users, setUsers] = useState<UserView[] | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [error, setError] = useState("");

  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [role, setRole] = useState<Role>("OFFICER");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    fetch("/_authority/auth/users")
      .then((r) => {
        if (r.status === 401) {
          window.location.href = "/login";
          return null;
        }
        if (r.status === 403) {
          setForbidden(true);
          return null;
        }
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      })
      .then((u) => {
        if (u) setUsers(u as UserView[]);
      })
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function createUser(e: React.FormEvent) {
    e.preventDefault();
    setStatus("");
    setBusy(true);
    try {
      const r = await fetch("/_authority/auth/users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, displayName, role, password }),
      });
      const data = await r.json().catch(() => ({}));
      if (!r.ok) {
        setStatus(data.error || "Could not create account (HTTP " + r.status + ")");
      } else {
        setStatus(`Created ${data.email} as ${ROLE_LABELS[data.role as Role]}.`);
        setEmail("");
        setDisplayName("");
        setPassword("");
        load();
      }
    } catch {
      setStatus("Could not reach the server");
    } finally {
      setBusy(false);
    }
  }

  if (forbidden) {
    return (
      <div className="reg-gov" style={{ minHeight: "100vh" }}>
        <div className="gm">
          <span className="sq" />
          <span className="nm">
            SwarmSight <span>· Accounts</span>
          </span>
        </div>
        <div className="mn">
          <h1>Not allowed</h1>
          <p className="lead">
            Account management is restricted to administrators.
          </p>
          <a className="lk" href="/">
            ← Back to the app
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="reg-gov" style={{ minHeight: "100vh" }}>
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Accounts</span>
        </span>
      </div>
      <div className="mn">
        <div className="crumb">
          <a className="lk" href="/">
            App
          </a>{" "}
          › Accounts
        </div>
        <h1>Manage accounts</h1>
        <p className="lead">
          Create accounts and assign the role that governs what each person may
          do. There is no public sign-up.
        </p>

        <div className="h2">Create an account</div>
        <form onSubmit={createUser} style={{ marginTop: 14, maxWidth: 460 }}>
          <Field label="Email">
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              style={inputStyle}
            />
          </Field>
          <Field label="Display name">
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              style={inputStyle}
            />
          </Field>
          <Field label="Role">
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
              style={inputStyle}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABELS[r]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Initial password (min 8 chars)">
            <input
              type="text"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              required
              style={inputStyle}
            />
          </Field>
          <button className="btn" type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create account"}
          </button>
          {status && (
            <div style={{ marginTop: 12, fontSize: 14, color: "#505a5f" }}>
              {status}
            </div>
          )}
        </form>

        <div className="h2" style={{ marginTop: 30 }}>
          Accounts <span className="c">{users ? users.length : ""}</span>
        </div>
        <div style={{ marginTop: 12 }}>
          {error ? (
            "Could not load accounts (" + error + ")."
          ) : !users ? (
            "Loading…"
          ) : (
            <table>
              <tbody>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                </tr>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>{u.displayName}</td>
                    <td>{u.email}</td>
                    <td>{ROLE_LABELS[u.role]}</td>
                    <td>{u.active ? "active" : "disabled"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label style={{ display: "block", marginBottom: 14 }}>
      <span style={{ display: "block", fontWeight: 700, marginBottom: 5 }}>
        {label}
      </span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  fontSize: 16,
  padding: "9px 11px",
  border: "2px solid #0b0c0c",
  fontFamily: "inherit",
  background: "#fff",
};
