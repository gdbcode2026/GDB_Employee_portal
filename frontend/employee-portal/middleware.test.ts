import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { middleware } from "@/middleware";
import { SESSION_COOKIE_NAME } from "@/lib/auth/config";

function requestFor(path: string, cookieHeader?: string): NextRequest {
  const headers = new Headers();
  if (cookieHeader) headers.set("cookie", cookieHeader);
  return new NextRequest(new Request(`http://localhost:3000${path}`, { headers }));
}

// Note on basePath: inside a REAL running Next.js server, `request.nextUrl.pathname` in
// middleware has the configured basePath ("/employees") already stripped, and
// `request.nextUrl.basePath` reports it separately - middleware.ts relies on exactly that to
// build the login redirect. A bare `new NextRequest(...)` constructed directly in a unit test
// (as here) does not run through that real server machinery, so `basePath` reads as "". This
// suite therefore tests middleware's path-classification/redirect-vs-passthrough LOGIC against
// unprefixed paths; the actual end-to-end basePath-prefixed redirect
// (http://localhost:3000/employees/api/auth/login) is verified for real in
// lib/auth/__tests__/integration.test.ts, against the real running server.
describe("middleware route protection", () => {
  it("redirects a protected route with no session cookie to the login route", () => {
    const response = middleware(requestFor("/profile"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toContain("/api/auth/login");
  });

  it("lets a protected route through when a session cookie is merely present (deeper validation happens downstream)", () => {
    const response = middleware(requestFor("/profile", `${SESSION_COOKIE_NAME}=some-opaque-id`));
    expect(response.status).toBe(200);
  });

  it("never redirects the auth routes themselves, even with no session cookie", () => {
    for (const path of ["/api/auth/login", "/api/auth/callback", "/api/auth/logout", "/api/auth/session"]) {
      const response = middleware(requestFor(path));
      expect(response.status).toBe(200);
    }
  });

  it("never redirects Next.js internals or obvious static assets", () => {
    for (const path of ["/_next/static/chunk.js", "/favicon.ico", "/styles.css"]) {
      const response = middleware(requestFor(path));
      expect(response.status).toBe(200);
    }
  });
});
