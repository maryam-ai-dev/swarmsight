"use client";

import { createContext, useContext, useEffect, useState } from "react";

export type Role = "ADMIN" | "SERVICE_OWNER" | "HEAD_OF_DEPARTMENT" | "OFFICER";

export interface AuthUser {
  id: string;
  email: string;
  role: Role;
  displayName: string;
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Administrator",
  SERVICE_OWNER: "Service owner",
  HEAD_OF_DEPARTMENT: "Head of department",
  OFFICER: "Officer",
};

interface UserContextValue {
  user: AuthUser | null;
  loading: boolean;
  // Demo-only "View as": lets a presenter see the app through each role's eyes
  // without re-authenticating. effectiveRole is what the UI should render; the
  // real token (and so real permissions) is unchanged.
  viewAs: Role | null;
  effectiveRole: Role | null;
  setViewAs: (r: Role | null) => void;
}

const UserContext = createContext<UserContextValue>({
  user: null,
  loading: true,
  viewAs: null,
  effectiveRole: null,
  setViewAs: () => {},
});

export function useUser() {
  return useContext(UserContext);
}

// Loads the signed-in user from Authority (through the proxy, carrying the
// session cookie). A 401 means the cookie is missing or expired, so we bounce to
// login. Wrap the authenticated app in this provider.
export function UserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [viewAs, setViewAs] = useState<Role | null>(null);

  useEffect(() => {
    fetch("/_authority/auth/me")
      .then((r) => {
        if (r.status === 401) {
          window.location.href = "/login";
          return null;
        }
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      })
      .then((u) => {
        if (u) setUser(u as AuthUser);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const effectiveRole = viewAs ?? user?.role ?? null;

  return (
    <UserContext.Provider
      value={{ user, loading, viewAs, effectiveRole, setViewAs }}
    >
      {children}
    </UserContext.Provider>
  );
}
