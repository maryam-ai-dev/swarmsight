"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next") || "/app";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const r = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!r.ok) {
        const data = await r.json().catch(() => ({}));
        setError(data.error || "Sign in failed");
        setBusy(false);
        return;
      }
      router.replace(next);
    } catch {
      setError("Could not reach the server");
      setBusy(false);
    }
  }

  return (
    <div className="reg-gov" style={{ minHeight: "100vh" }}>
      <div className="gm">
        <span className="sq" />
        <span className="nm">
          SwarmSight <span>· Sign in</span>
        </span>
      </div>
      <div className="mn" style={{ maxWidth: 420 }}>
        <h1>Sign in</h1>
        <p className="lead">
          This is a governed service. Accounts are issued by an administrator.
        </p>
        <form onSubmit={submit} style={{ marginTop: 20 }}>
          {error && (
            <div
              className="gp"
              style={{
                borderLeft: "4px solid #d4351c",
                background: "#fef4f3",
                marginBottom: 16,
                padding: "12px 16px",
              }}
            >
              <span style={{ color: "#942514", fontWeight: 700 }}>{error}</span>
            </div>
          )}
          <label style={{ display: "block", marginBottom: 14 }}>
            <span
              style={{ display: "block", fontWeight: 700, marginBottom: 5 }}
            >
              Email
            </span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
              required
              style={inputStyle}
            />
          </label>
          <label style={{ display: "block", marginBottom: 20 }}>
            <span
              style={{ display: "block", fontWeight: 700, marginBottom: 5 }}
            >
              Password
            </span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              style={inputStyle}
            />
          </label>
          <button className="btn" type="submit" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  fontSize: 16,
  padding: "9px 11px",
  border: "2px solid #0b0c0c",
  fontFamily: "inherit",
};

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}
