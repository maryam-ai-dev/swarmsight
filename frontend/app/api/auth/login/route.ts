import { NextResponse } from "next/server";

// Server-side login. The browser posts credentials here (same origin); this
// handler calls Authority, and on success stores the returned JWT in an httpOnly
// cookie the browser cannot read. Every later API call carries that cookie
// through the proxy to Authority. The token never reaches client JavaScript.
const AUTHORITY_ORIGIN = process.env.AUTHORITY_ORIGIN || "http://localhost:8080";
const SESSION_COOKIE = "sws_session";
const EIGHT_HOURS = 60 * 60 * 8;

export async function POST(req: Request) {
  let body: { email?: string; password?: string };
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }

  const r = await fetch(`${AUTHORITY_ORIGIN}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: body.email, password: body.password }),
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
      { error: err.error || "Invalid email or password" },
      { status: 401 },
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
