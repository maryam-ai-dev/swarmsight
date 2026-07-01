import { NextRequest, NextResponse } from "next/server";

// Route guard. The public landing ("/") and the login page are open to anyone;
// everything else (the governed app at /app, account management at /admin) needs
// a session cookie, or the request is bounced to /login. This is a presence
// check for fast UX; Authority still verifies the token's signature and expiry
// on every API call, so a forged or stale cookie gets no data. API/proxy paths
// pass through untouched and answer 401 themselves.
const SESSION_COOKIE = "sws_session";
const PUBLIC_PATHS = ["/", "/login"];
const PASSTHROUGH = [
  "/_next",
  "/_authority",
  "/_intelligence",
  "/api/auth",
  "/favicon.ico",
];

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;

  if (PASSTHROUGH.some((p) => pathname.startsWith(p))) {
    return NextResponse.next();
  }
  if (PUBLIC_PATHS.includes(pathname)) {
    return NextResponse.next();
  }
  if (req.cookies.get(SESSION_COOKIE)) {
    return NextResponse.next();
  }

  // Remember where they were headed, including any query (e.g. ?screen=check),
  // so login can return them there.
  const loginUrl = new URL("/login", req.url);
  loginUrl.searchParams.set("next", pathname + req.nextUrl.search);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image).*)"],
};
