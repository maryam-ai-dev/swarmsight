"use client";

import { ScreenId } from "@/lib/nav";

// Mirrors the original `.screen` / `.screen.on` show-hide model: every screen
// stays mounted and is toggled by the `on` class, so each screen's data fetch
// runs once on mount exactly as the demo's top-level script did.
export function Screen({
  id,
  active,
  brand,
  children,
}: {
  id: ScreenId;
  active: ScreenId;
  brand: "reg-brand" | "reg-gov" | "reg-pp" | "reg-mask";
  children: React.ReactNode;
}) {
  return (
    <section className={`screen ${brand}${active === id ? " on" : ""}`} id={id}>
      {children}
    </section>
  );
}
