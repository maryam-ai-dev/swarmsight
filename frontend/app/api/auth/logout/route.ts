import { NextResponse } from "next/server";

// Clears the session cookie. Stateless tokens are not revoked server-side, but
// removing the cookie ends the session in this browser.
export async function POST() {
  const res = NextResponse.json({ ok: true });
  res.cookies.set("sws_session", "", { httpOnly: true, path: "/", maxAge: 0 });
  return res;
}
