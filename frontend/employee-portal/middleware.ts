import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE_NAME } from "@/lib/auth/config";

/**
 * Deliberately minimal: a cheap presence-only check on the session cookie, nothing more. It does
 * NOT look up Redis, does NOT verify the access token, and does NOT make any authorization
 * decision - that stays with the backend (and, for "is this session actually still valid," with
 * lib/auth/session.ts's getSession, invoked deeper in the request - e.g. by RootLayout's own
 * apiClient call, which already renders AuthRequiredNotice on a 401 exactly as it did before this
 * task). A present-but-expired/invalid cookie is let through here and corrected one layer in,
 * not duplicated here - this is intentionally not "complex authorization logic."
 *
 * Excludes the auth routes themselves (must stay reachable while logged out), Next.js's own
 * internals, and common static-asset requests - never blindly redirects everything.
 */
function isPubliclyReachable(pathname: string): boolean {
  if (pathname.includes("/api/auth/")) return true;
  if (pathname.includes("/_next/")) return true;
  if (pathname === "/favicon.ico" || pathname.endsWith("/favicon.ico")) return true;
  if (/\.(?:png|jpg|jpeg|svg|gif|webp|ico|css|js|map|txt|woff|woff2)$/.test(pathname)) return true;
  return false;
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (isPubliclyReachable(pathname)) {
    return NextResponse.next();
  }

  const hasSessionCookie = request.cookies.has(SESSION_COOKIE_NAME);
  if (hasSessionCookie) {
    return NextResponse.next();
  }

  const loginUrl = new URL(`${request.nextUrl.basePath}/api/auth/login`, request.nextUrl.origin);
  return NextResponse.redirect(loginUrl);
}

// Matches unconditionally (including the bare basePath root, "/employees" itself) and leaves
// ALL exclusion logic to isPubliclyReachable() above. A negative-lookahead matcher regex was
// tried first and is NOT safe here: it was observed, against the real running app, to never
// invoke this middleware at all for the exact basePath root ("/employees" with no further
// segment) while correctly invoking it for every other path including "/employees/" - a
// reproducible gap that would have left the dashboard homepage completely unprotected. Matching
// everything and excluding in code, already covered by middleware.test.ts, is the robust choice.
export const config = {
  matcher: ["/:path*"],
};
