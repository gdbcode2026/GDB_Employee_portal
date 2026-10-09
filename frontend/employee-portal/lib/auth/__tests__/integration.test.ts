import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { spawn, type ChildProcess } from "child_process";
import http from "http";
import crypto from "crypto";

/**
 * End-to-end integration tests against the REAL local Keycloak instance
 * (docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md) and a real Redis instance, plus a real running
 * copy of this Next.js app (started here via `next dev`, not mocked). No fake Keycloak behavior
 * is used anywhere in this file - every login goes through Keycloak's actual login form and
 * actual Authorization Code + PKCE token exchange, exactly as a browser+BFF would.
 *
 * Requires: `docker compose -f infrastructure/docker/compose.yaml up -d` (Keycloak + Redis)
 * running first - same precondition the backend's own Testcontainers-based tests have for
 * Docker. Boots its own Next.js dev server, and a tiny local HTTP stub standing in for the API
 * Gateway (GATEWAY_URL) so the Bearer-token-bridge assertion doesn't require the entire Spring
 * Boot platform to be running - everything upstream of that one boundary (Keycloak login, token
 * exchange/verification, Redis session storage, cookie issuance) is 100% real.
 *
 * Runs on port 3000 deliberately - Keycloak's registered redirect URI pattern
 * (docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md) is `http://localhost:3000/employees/*`, matching
 * `npm run dev`'s own fixed port. Stop any already-running dev server before running this suite.
 */

const APP_PORT = 3000;
const APP_BASE = `http://localhost:${APP_PORT}/employees`;
const KEYCLOAK_BASE = "http://localhost:8180/realms/gdb/protocol/openid-connect";
const KEYCLOAK_CLIENT_ID = "gdb-employee-portal";
const KEYCLOAK_CLIENT_SECRET = "local-development-only-client-secret";
const TEST_PASSWORD = "local-development-only";

let appProcess: ChildProcess;
let stub: http.Server;
let stubPort: number;
let lastStubRequest: { headers: http.IncomingHttpHeaders; url: string } | null = null;

function b64url(buf: Buffer): string {
  return buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

interface RawResponse {
  status: number;
  headers: http.IncomingHttpHeaders;
  body: string;
}

function rawRequest(
  method: string,
  url: string,
  opts: { headers?: Record<string, string>; body?: string } = {},
): Promise<RawResponse> {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.request(
      { method, hostname: u.hostname, port: u.port, path: u.pathname + (u.search || ""), headers: opts.headers },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => resolve({ status: res.statusCode ?? 0, headers: res.headers, body: data }));
      },
    );
    req.on("error", reject);
    if (opts.body) req.write(opts.body);
    req.end();
  });
}

function cookieHeaderFrom(setCookie: string[] | undefined): string {
  return (setCookie ?? []).map((c) => c.split(";")[0]).join("; ");
}

/** The callback now sets two cookies (clears gdb_oauth_txn, sets gdb_session) - callers that just want "the session cookie header value" need this one specifically. */
function sessionCookieHeaderFrom(setCookie: string[] | undefined): string {
  const line = (setCookie ?? []).find((c) => c.startsWith("gdb_session="));
  return line ? line.split(";")[0] : "";
}

interface LoginTransaction {
  transactionCookie: string;
  code: string;
  state: string;
  callbackUrl: string;
}

/**
 * Drives /login through a real Keycloak login form and stops right before the callback, so
 * callers can choose exactly which cookie (if any) to attach when they actually call back -
 * this is what lets the H-1 regression tests below simulate "a different browser."
 */
async function completeKeycloakLoginAs(username: string): Promise<LoginTransaction> {
  const loginResp = await rawRequest("GET", `${APP_BASE}/api/auth/login`);
  expect(loginResp.status).toBe(307);
  const authorizationUrl = loginResp.headers.location!;
  const transactionCookie = cookieHeaderFrom(loginResp.headers["set-cookie"]);
  expect(transactionCookie).toMatch(/^gdb_oauth_txn=/);

  const authGet = await rawRequest("GET", authorizationUrl);
  expect(authGet.status).toBe(200);
  const kcCookie = cookieHeaderFrom(authGet.headers["set-cookie"]);
  const formAction = authGet.body.match(/action="([^"]+)"/)![1].replace(/&amp;/g, "&");

  const loginBody = `username=${encodeURIComponent(username)}&password=${encodeURIComponent(TEST_PASSWORD)}&credentialId=`;
  const loginPost = await rawRequest("POST", formAction, {
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "Content-Length": String(Buffer.byteLength(loginBody)),
      Cookie: kcCookie,
    },
    body: loginBody,
  });
  expect(loginPost.status).toBe(302);
  const callbackUrl = loginPost.headers.location!;
  const callbackParams = new URL(callbackUrl).searchParams;

  return {
    transactionCookie,
    code: callbackParams.get("code")!,
    state: callbackParams.get("state")!,
    callbackUrl,
  };
}

/** Performs a genuine Authorization Code + PKCE login through our app's real routes and the real Keycloak login form, attaching the correct transaction cookie at the callback. Returns the app's session cookie header value (e.g. "gdb_session=abc123"). */
async function realLoginAs(username: string): Promise<string> {
  const txn = await completeKeycloakLoginAs(username);
  const callbackResp = await rawRequest("GET", txn.callbackUrl, { headers: { Cookie: txn.transactionCookie } });
  expect(callbackResp.status).toBe(307);
  expect(callbackResp.headers.location).toBe(`${APP_BASE}/`);
  return sessionCookieHeaderFrom(callbackResp.headers["set-cookie"]);
}

function waitForServer(url: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      http
        .get(url, (res) => {
          res.resume();
          resolve();
        })
        .on("error", () => {
          if (Date.now() > deadline) reject(new Error("app server did not become ready in time"));
          else setTimeout(attempt, 500);
        });
    };
    attempt();
  });
}

beforeAll(async () => {
  stub = http.createServer((req, res) => {
    lastStubRequest = { headers: req.headers, url: req.url ?? "" };
    res.writeHead(200, { "Content-Type": "application/json" });
    if (req.url === "/api/v1/employees/me") {
      res.end(
        JSON.stringify({
          id: "11111111-1111-1111-1111-111111111111",
          employeeNumber: "E-TEST",
          firstName: "Test",
          lastName: "Stub",
          email: "test.stub@gdb.local",
          phone: null,
          status: "ACTIVE",
          employment: null,
          emergencyContacts: [],
          createdAt: new Date().toISOString(),
        }),
      );
      return;
    }
    res.end(JSON.stringify({ items: [], page: { number: 0, size: 0, total: 0 } }));
  });
  await new Promise<void>((resolve) => stub.listen(0, resolve));
  stubPort = (stub.address() as { port: number }).port;

  // `shell: true` is required on Windows to run the `npx.cmd` batch file at all (a direct,
  // shell-less spawn of it fails with EINVAL). That interposes a cmd.exe wrapper around the real
  // `next dev` node process, so afterAll below uses `taskkill /T` (kill the whole descendant
  // tree), not a plain kill() of just the wrapper - a plain kill() was observed to leave the
  // real node process running and still bound to :3000 after the suite exited.
  appProcess = spawn("npx", ["next", "dev", "--port", String(APP_PORT)], {
    cwd: process.cwd(),
    env: { ...process.env, GATEWAY_URL: `http://localhost:${stubPort}` },
    shell: true,
    stdio: "pipe",
  });
  await waitForServer(`${APP_BASE}/api/auth/session`, 60_000);
}, 90_000);

afterAll(async () => {
  stub.close();
  if (appProcess.pid) {
    if (process.platform === "win32") {
      await new Promise((resolve) => {
        spawn("taskkill", ["/PID", String(appProcess.pid), "/T", "/F"]).on("exit", resolve);
      });
    } else {
      appProcess.kill();
    }
  }
});

describe("unauthenticated state", () => {
  it("session endpoint reports authenticated:false with no cookie", async () => {
    const resp = await rawRequest("GET", `${APP_BASE}/api/auth/session`);
    expect(resp.status).toBe(200);
    expect(JSON.parse(resp.body)).toEqual({ authenticated: false });
  });

  it("a protected page redirects (via middleware) to the login route", async () => {
    const resp = await rawRequest("GET", `${APP_BASE}/profile`);
    expect(resp.status).toBe(307);
    // Next.js may emit this as a same-origin-relative Location (valid per RFC 9110, section
    // 10.2.2) rather than an absolute URL - check the path, not exact string equality.
    expect(new URL(resp.headers.location!, APP_BASE).pathname).toBe("/employees/api/auth/login");
  });
});

describe("login route", () => {
  it("redirects to Keycloak's real authorization endpoint with Authorization Code + PKCE S256 parameters", async () => {
    const resp = await rawRequest("GET", `${APP_BASE}/api/auth/login`);
    expect(resp.status).toBe(307);
    const location = new URL(resp.headers.location!);
    expect(location.origin + location.pathname).toBe(`${KEYCLOAK_BASE}/auth`);
    expect(location.searchParams.get("client_id")).toBe(KEYCLOAK_CLIENT_ID);
    expect(location.searchParams.get("response_type")).toBe("code");
    expect(location.searchParams.get("code_challenge_method")).toBe("S256");
    expect(location.searchParams.get("code_challenge")).toBeTruthy();
    expect(location.searchParams.get("state")).toBeTruthy();
  });
});

describe("full login flow", () => {
  it("employee.test can authenticate end-to-end and receive a session cookie", async () => {
    const cookie = await realLoginAs("employee.test");
    expect(cookie).toMatch(/^gdb_session=/);

    const sessionResp = await rawRequest("GET", `${APP_BASE}/api/auth/session`, { headers: { Cookie: cookie } });
    const session = JSON.parse(sessionResp.body);
    expect(session.authenticated).toBe(true);
    expect(session.roles).toEqual(["EMPLOYEE"]);
    expect(Array.isArray(session.permissions)).toBe(true);
    expect(session.permissions.length).toBeGreaterThan(0);
    expect(typeof session.subject).toBe("string");
  });

  it("session endpoint never exposes the access token, refresh token, or any session-internal field", async () => {
    const cookie = await realLoginAs("manager.test");
    const sessionResp = await rawRequest("GET", `${APP_BASE}/api/auth/session`, { headers: { Cookie: cookie } });
    expect(sessionResp.body).not.toMatch(/access_?[Tt]oken/);
    expect(sessionResp.body).not.toMatch(/refresh_?[Tt]oken/);
    expect(sessionResp.body).not.toMatch(/id_?[Tt]oken/);
    expect(sessionResp.body).not.toMatch(/client_secret/);
    expect(sessionResp.body).not.toMatch(/codeVerifier/);
    // A real Keycloak access token is a three-part dot-separated JWT - confirm none is present.
    expect(sessionResp.body).not.toMatch(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
  });

  it("no response in the flow ever hands the raw Keycloak token set to the browser", async () => {
    const txn = await completeKeycloakLoginAs("hr.test");
    const callbackResp = await rawRequest("GET", txn.callbackUrl, { headers: { Cookie: txn.transactionCookie } });
    // The callback's own response body (whatever the browser would actually receive) must not
    // contain the access token in any form - only a Location header + an opaque cookie value.
    expect(callbackResp.body).not.toMatch(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
    const setCookie = (callbackResp.headers["set-cookie"] ?? []).join(" ");
    expect(setCookie).not.toMatch(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
  });
});

// Every callback failure now returns this exact same generic body (security review finding
// M-4 fix) - the specific reason is server-log-only. Every test below checks only this shape.
function expectGenericAuthFailure(resp: RawResponse): void {
  expect(resp.status).toBe(400);
  expect(JSON.parse(resp.body)).toEqual({ error: "authentication_failed" });
}

describe("callback state validation", () => {
  it("rejects a callback missing the code/state parameters entirely", async () => {
    const resp = await rawRequest("GET", `${APP_BASE}/api/auth/callback`);
    expectGenericAuthFailure(resp);
  });

  it("surfaces an upstream Keycloak error parameter as a real failure, not a fake success", async () => {
    const resp = await rawRequest(
      "GET",
      `${APP_BASE}/api/auth/callback?error=access_denied&error_description=user+cancelled`,
    );
    expectGenericAuthFailure(resp);
  });

  it("rejects a forged/unknown state when no transaction cookie is present either", async () => {
    const resp = await rawRequest(
      "GET",
      `${APP_BASE}/api/auth/callback?code=forged-code&state=${crypto.randomBytes(16).toString("hex")}`,
    );
    expectGenericAuthFailure(resp);
  });

  it("rejects a callback with a real, unconsumed state but NO transaction cookie attached", async () => {
    // Real /login call, real transaction stored in Redis - but the request below deliberately
    // omits the Cookie header entirely, simulating a browser that never initiated this login.
    const loginResp = await rawRequest("GET", `${APP_BASE}/api/auth/login`);
    const state = new URL(loginResp.headers.location!).searchParams.get("state")!;
    const resp = await rawRequest("GET", `${APP_BASE}/api/auth/callback?code=irrelevant&state=${state}`);
    expectGenericAuthFailure(resp);
  });

  it("rejects a callback whose transaction cookie does not match the query-string state (mismatched cookie/state)", async () => {
    const txnA = await completeKeycloakLoginAs("employee.test");
    // Attach transaction A's cookie (a real, valid, unconsumed one - just for the WRONG state)
    // while requesting transaction A's own real callback URL with a tampered `state` value, so
    // the mismatch is unambiguous: the cookie says one state, the query string says another.
    const tamperedUrl = txnA.callbackUrl.replace(`state=${txnA.state}`, `state=${txnA.state}-tampered`);
    const resp = await rawRequest("GET", tamperedUrl, { headers: { Cookie: txnA.transactionCookie } });
    expectGenericAuthFailure(resp);
  });

  it("rejects an expired transaction (real TTL expiry in Redis, not just a missing key)", async () => {
    const { redisClient } = await import("@/lib/auth/redis");
    const { generateState, generateCodeVerifier } = await import("@/lib/auth/pkce");
    const state = generateState();
    const codeVerifier = generateCodeVerifier();
    // Store it directly with a 1ms TTL instead of waiting out the real 10-minute window.
    await redisClient().set(
      `gdb:auth-txn:${state}`,
      JSON.stringify({ codeVerifier, redirectUri: `${APP_BASE}/api/auth/callback`, createdAt: Date.now() }),
      "PX",
      1,
    );
    await new Promise((resolve) => setTimeout(resolve, 50));
    const resp = await rawRequest("GET", `${APP_BASE}/api/auth/callback?code=irrelevant&state=${state}`, {
      headers: { Cookie: `gdb_oauth_txn=${state}` },
    });
    expectGenericAuthFailure(resp);
  });

  it("a consumed transaction cannot be replayed", async () => {
    const txn = await completeKeycloakLoginAs("manager.test");
    const first = await rawRequest("GET", txn.callbackUrl, { headers: { Cookie: txn.transactionCookie } });
    expect(first.status).toBe(307); // succeeds once

    const replay = await rawRequest("GET", txn.callbackUrl, { headers: { Cookie: txn.transactionCookie } });
    expectGenericAuthFailure(replay);
  });

  it("H-1 regression: a transaction created by one browser cannot be completed by a different browser", async () => {
    // "Attacker" (or simply a separate browser/device) performs a real, complete login,
    // obtaining a genuine, not-yet-consumed (code, state) pair and its own transaction cookie.
    const attackerTxn = await completeKeycloakLoginAs("employee.test");

    // The attacker relays the resulting callback URL (code + state) to a victim, but the
    // victim's browser has no cookie for this transaction at all - it never called /login for
    // it. This is the exact exploit the security review described: before the H-1 fix, this
    // request succeeded and planted a session (authenticated as whoever the attacker's
    // credentials resolved to) in the "victim" request's cookie jar.
    const victimResp = await rawRequest("GET", attackerTxn.callbackUrl); // no Cookie header at all
    expectGenericAuthFailure(victimResp);
    expect(victimResp.headers["set-cookie"]?.some((c) => c.startsWith("gdb_session="))).toBeFalsy();

    // The cookie check happens *before* the Redis transaction is ever touched (confirmed by
    // reading app/api/auth/callback/route.ts directly), so the above rejection did not consume
    // it - the browser that actually holds the matching transaction cookie can still complete
    // its own, real login normally. This is the key distinction from the pre-fix behavior: it is
    // no longer "whoever requests the callback URL first wins" - it is specifically "only the
    // browser bearing the matching cookie can ever complete this transaction," which is exactly
    // what prevents the victim's browser (with no matching cookie) from ever succeeding, while
    // leaving ordinary, same-browser logins completely unaffected.
    const legitimateCompletion = await rawRequest("GET", attackerTxn.callbackUrl, {
      headers: { Cookie: attackerTxn.transactionCookie },
    });
    expect(legitimateCompletion.status).toBe(307);
    expect(legitimateCompletion.headers["set-cookie"]?.some((c) => c.startsWith("gdb_session="))).toBe(true);
  });
});

describe("cookie security attributes", () => {
  it("the session cookie set by callback is HttpOnly, SameSite=Lax, Path=/employees", async () => {
    const txn = await completeKeycloakLoginAs("finance.test");
    const callbackResp = await rawRequest("GET", txn.callbackUrl, { headers: { Cookie: txn.transactionCookie } });
    expect(callbackResp.status).toBe(307);
    const setCookieLine = (callbackResp.headers["set-cookie"] ?? []).find((c) => c.startsWith("gdb_session="))!;
    expect(setCookieLine).toContain("HttpOnly");
    expect(setCookieLine).toMatch(/SameSite=Lax/i);
    expect(setCookieLine).toContain("Path=/employees");
    // Local HTTP development: Secure is intentionally absent here (see
    // docs/security/NEXTJS_BFF_AUTHENTICATION.md's documented local-dev/production tradeoff) -
    // `next dev` runs with NODE_ENV=development, not production.
    expect(setCookieLine).not.toMatch(/Secure/i);
  });

  it("the transaction cookie set by /login is HttpOnly, SameSite=Lax, and scoped only to the callback path", async () => {
    const loginResp = await rawRequest("GET", `${APP_BASE}/api/auth/login`);
    const setCookieLine = (loginResp.headers["set-cookie"] ?? []).find((c) => c.startsWith("gdb_oauth_txn="))!;
    expect(setCookieLine).toBeTruthy();
    expect(setCookieLine).toContain("HttpOnly");
    expect(setCookieLine).toMatch(/SameSite=Lax/i);
    expect(setCookieLine).toContain("Path=/employees/api/auth/callback");
    expect(setCookieLine).not.toMatch(/Secure/i);
  });

  it("the transaction cookie is cleared (Max-Age=0) after both a successful and a failed callback", async () => {
    const successTxn = await completeKeycloakLoginAs("admin.test");
    const successResp = await rawRequest("GET", successTxn.callbackUrl, { headers: { Cookie: successTxn.transactionCookie } });
    const clearedOnSuccess = (successResp.headers["set-cookie"] ?? []).find((c) => c.startsWith("gdb_oauth_txn="))!;
    expect(clearedOnSuccess).toMatch(/Max-Age=0/i);

    const failResp = await rawRequest("GET", `${APP_BASE}/api/auth/callback?code=x&state=y`, {
      headers: { Cookie: "gdb_oauth_txn=some-other-state" },
    });
    const clearedOnFailure = (failResp.headers["set-cookie"] ?? []).find((c) => c.startsWith("gdb_oauth_txn="))!;
    expect(clearedOnFailure).toMatch(/Max-Age=0/i);
  });
});

describe("API client bearer-token bridge", () => {
  it("attaches the real session's access token as Authorization: Bearer when calling the gateway, and the browser-facing response never contains it", async () => {
    const cookie = await realLoginAs("admin.test");

    lastStubRequest = null;
    const pageResp = await rawRequest("GET", `${APP_BASE}`, { headers: { Cookie: cookie } });
    expect(pageResp.status).toBe(200);

    expect(lastStubRequest).not.toBeNull();
    const authHeader = lastStubRequest!.headers["authorization"];
    expect(authHeader).toMatch(/^Bearer /);
    expect(authHeader!.split(".").length).toBe(3); // a real JWT reached the stub gateway

    // The actual HTML the browser receives must never contain the bearer token/header.
    expect(pageResp.body).not.toContain(authHeader);
    expect(pageResp.body).not.toMatch(/Authorization:\s*Bearer/i);
  });

  it("sends no Authorization header at all for an unauthenticated request (unchanged prior behavior)", async () => {
    lastStubRequest = null;
    await rawRequest("GET", `${APP_BASE}/attendance`);
    // Middleware already redirected this away from rendering the page, but confirm no stray
    // bearer token is ever attached without a session regardless. (TS can't narrow across the
    // async stub-server callback that may have reassigned this, hence the `as` read-through.)
    const maybeRequest = lastStubRequest as { headers: http.IncomingHttpHeaders; url: string } | null;
    if (maybeRequest) {
      expect(maybeRequest.headers["authorization"]).toBeUndefined();
    }
  });
});

describe("logout", () => {
  it("clears the session, invalidates it server-side, and returns the user to an unauthenticated state", async () => {
    const cookie = await realLoginAs("superadmin.test");

    const beforeLogout = await rawRequest("GET", `${APP_BASE}/api/auth/session`, { headers: { Cookie: cookie } });
    expect(JSON.parse(beforeLogout.body).authenticated).toBe(true);

    const logoutResp = await rawRequest("GET", `${APP_BASE}/api/auth/logout`, { headers: { Cookie: cookie } });
    expect(logoutResp.status).toBe(307); // NextResponse.redirect()'s default status
    expect(logoutResp.headers.location).toContain("/realms/gdb/protocol/openid-connect/logout");
    const clearedCookie = (logoutResp.headers["set-cookie"] ?? []).find((c) => c.startsWith("gdb_session="))!;
    expect(clearedCookie).toMatch(/Max-Age=0/i);

    // Re-opening the (now cleared) session reports unauthenticated again.
    const afterLogout = await rawRequest("GET", `${APP_BASE}/api/auth/session`, { headers: { Cookie: cookie } });
    expect(JSON.parse(afterLogout.body)).toEqual({ authenticated: false });
  });
});

describe("expired / invalid session handling", () => {
  it("a session cookie referencing a session ID that was never created is treated as unauthenticated, not a crash", async () => {
    const resp = await rawRequest("GET", `${APP_BASE}/api/auth/session`, {
      headers: { Cookie: "gdb_session=this-session-id-does-not-exist-anywhere" },
    });
    expect(resp.status).toBe(200);
    expect(JSON.parse(resp.body)).toEqual({ authenticated: false });
  });

  it("an authenticated page falls back to AuthRequiredNotice-driving behavior (401 upstream) once the session is invalid", async () => {
    // A garbage session cookie means getCurrentSession() returns null, so apiClient attaches no
    // Authorization header; against the real Gateway/services (not this test's stub) that would
    // 401 exactly as it already did before this task existed. Verified here at the bridge level:
    // no bearer header is sent, which is the precondition for that existing 401 behavior.
    lastStubRequest = null;
    await rawRequest("GET", `${APP_BASE}`, { headers: { Cookie: "gdb_session=garbage" } });
    expect(lastStubRequest!.headers["authorization"]).toBeUndefined();
  });
});
