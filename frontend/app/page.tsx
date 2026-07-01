"use client";

import { useRouter } from "next/navigation";
import { NavContext, ScreenId } from "@/lib/nav";
import { Landing } from "@/components/screens/Landing";

// The public front door. Anyone can see the landing page; its calls to action
// lead into the governed app at /app, which requires a login. The Landing
// component is reused unchanged: its in-page links call NavContext.go, which
// here routes to /app?screen=<id> instead of toggling a client screen.
export default function PublicLanding() {
  const router = useRouter();

  function go(id: ScreenId) {
    router.push(id === "landing" ? "/app" : `/app?screen=${id}`);
  }

  // Rendered full-bleed (no .frame card): the landing's own reg-brand background
  // fills the viewport. The boxed frame is only used inside the app at /app.
  return (
    <NavContext.Provider value={{ active: "landing", go }}>
      <a href="/app" className="landing-signin">
        Sign in
      </a>
      <div className="landing-full">
        <Landing active="landing" />
      </div>
    </NavContext.Provider>
  );
}
