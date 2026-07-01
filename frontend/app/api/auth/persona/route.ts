import { NextResponse } from "next/server";

// Demo-only persona switch. The browser posts a persona email here; this handler
// forwards the caller's session to Authority's demo-login (which is gated to the
// seeded demo accounts and to demo-seed mode), then stores the new token in the
// httpOnly cookie. The client reloads and is now that persona, real role, real
// permissions, so "View as" shows each account's true screen and controls.
const AUTHORITY_ORIGIN = process.env.AUTHORITY_ORIGIN || "http://localhost:8080";
const SESSION_COOKIE = "sws_session";
const EIGHT_HOURS = 60 * 60 * 8;

export async function POST(req: Request) {
  let body: { email?: string };
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }

  const r = await fetch(`${AUTHORITY_ORIGIN}/auth/demo-login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // Forward the current session so Authority authorises the switch.
      cookie: req.headers.get("cookie") || "",
    },
    body: JSON.stringify({ email: body.email }),
  }).catch(() => null);

  if (!r) {
    return NextResponse.json(
      { error: "Could not reach the authentication service" },
      { status: 502 },
    );
  }
  if (!r.ok) {
    const err = await r.json().catch(() => ({}));
    return NextResponse.json(
      { error: err.error || "Persona switch not allowed" },
      { status: r.status },
    );
  }

  const data = (await r.json()) as { token: string; user: unknown };
  const res = NextResponse.json({ user: data.user });
  res.cookies.set(SESSION_COOKIE, data.token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: EIGHT_HOURS,
  });
  return res;
}
