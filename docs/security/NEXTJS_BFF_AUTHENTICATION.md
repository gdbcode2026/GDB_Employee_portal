# Next.js BFF Authentication

The first production-oriented authentication slice for the Employee Portal: real login,
Keycloak integration, server-side session, and a server-side bearer-token bridge from the BFF to
the API Gateway. The browser never receives, stores, or has JavaScript access to the Keycloak
access token at any point.

## Browser authentication flow

```
Browser                    Next.js BFF                      Keycloak
  |-- GET /employees/profile -->|
  |                              |-- cookie absent, middleware redirects -->|
  |<-- 307 /api/auth/login ------|
  |-- GET /api/auth/login ------>|
  |                              | generates state + PKCE verifier/challenge,
  |                              | stores {state -> verifier} in Redis (10 min TTL)
  |<-- 307 Keycloak /auth -------|
  |-- GET /auth?...code_challenge...-------------------------------------->|
  |<-- real Keycloak login form --------------------------------------------|
  |-- POST credentials ----------------------------------------------------->|
  |<-- 302 /api/auth/callback?code=...&state=... ----------------------------|
  |-- GET /api/auth/callback?code&state -->|
  |                              | consumes {state->verifier} (one-time),
  |                              | exchanges code+verifier for tokens (server-side, with
  |                              |   client_secret - never exposed to the browser),
  |                              | verifies the access token (signature via JWKS, issuer,
  |                              |   audience - jose, same checks the backend already does),
  |                              | creates a Redis session record, generates an opaque
  |                              |   session ID
  |<-- 307 /employees/ + Set-Cookie: gdb_session=<opaque id> (HttpOnly) --|
  |-- GET /employees (Cookie: gdb_session=...) -->|
  |                              | looks up session in Redis, attaches
  |                              |   Authorization: Bearer <access token> server-side
  |                              |-- GET /api/v1/employees/me, Bearer <token> -->| API Gateway
  |<-- rendered page (no token in the response) --|
```

Only the Set-Cookie line and the final rendered page ever reach the browser. The access token,
refresh token, ID token, PKCE verifier, and client secret never appear in any response the
browser receives, at any step - verified directly (see Testing below), not assumed.

## Keycloak integration

Reuses the existing `gdb` realm/`gdb-employee-portal` client exactly as already configured in
`docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md` - no realm/client/role/mapper changes were made by
this task. `lib/auth/oidc.ts` performs OIDC discovery against `KEYCLOAK_ISSUER`, caches the
discovery document and JWKS in memory, and exposes exactly four operations: build the
authorization URL, exchange a code for tokens, refresh an access token, and verify an access
token (signature + issuer + audience - the same three checks `EmployeeSecurityConfig`/every other
service's `SecurityConfig` already performs independently; this task does not weaken, replace, or
duplicate that backend validation, it mirrors its semantics server-side in the BFF for the one
narrow purpose of deciding whether a session is still good).

## Session architecture

A server-side session record lives in **Redis** (already running in
`infrastructure/docker/compose.yaml` for the gateway's own rate limiting - this reuses that
instance rather than introducing a new permanent datastore solely for sessions, per this task's
own instruction to prefer that if suitable). The browser cookie carries only a 32-byte random
opaque session ID (`lib/auth/pkce.ts`'s `generateSessionId`) - never the access token, never any
claim, never anything reversible to either.

Stored per session (`lib/auth/session.ts`): `sub`, `accessToken`, `refreshToken`, `idToken`,
`accessTokenExpiresAt`, `roles[]`, `permissions[]`, `createdAt`. No unnecessary personal
information (no name/email) is stored.

**Token refresh**: `getSession`/`getCurrentSession` transparently refreshes the access token via
the refresh-token grant when it is within 30 seconds of expiring, updating the Redis record
in-place. If the refresh token is itself expired/revoked, or Keycloak is unreachable, the session
is deleted and treated as logged-out (fail closed - never served stale).

**Session lifetime**: the Redis key carries an 8-hour TTL as an absolute backstop (one working
day for an internal portal), independent of the refresh mechanism above, which is the actual
day-to-day mechanism keeping a session alive.

**Tradeoff accepted, documented rather than hidden**: this is the smallest secure server-side
session store that fits the existing architecture, not a full session-management subsystem (no
device listing, no "sign out everywhere," no sliding-window renewal policy beyond the refresh
grant). See "Known limitations" below.

## Cookie properties

| Attribute | Value | Why |
|---|---|---|
| Name | `gdb_session` | |
| HttpOnly | `true` | Never readable from browser JavaScript |
| SameSite | `Lax` | |
| Path | `/employees` | Scoped to this app's basePath, never sent to unrelated paths |
| Secure | `true` in production, `false` in local dev | See below |
| Max-Age | 28800s (8h), or `0` to clear on logout | Matches the session TTL |

**The Secure/local-dev decision, made explicitly, not silently** (`lib/auth/cookie.ts`):
`Secure` requires HTTPS. Local development serves plain `http://localhost` - a `Secure` cookie
would simply never be sent by the browser at all, breaking login entirely. `isProductionEnvironment()`
reads `NODE_ENV` (set to `"production"` by `next build`/`next start`, `"development"` by
`next dev`) exactly once, in one place, so this is a single controlled decision rather than
scattered conditionals. **Production MUST run behind HTTPS** for `Secure` to take effect at all;
this is a deployment requirement, not something this code can enforce on its own.

## Server-side token handling (the BFF bridge)

`lib/api/client.ts` (the existing, unmodified-in-shape API client every page already used) now
also looks up the current session server-side and attaches `Authorization: Bearer <access token>`
to every outgoing request, when a session exists. With no session (or one that could not be kept
alive), a request proceeds exactly as it already did before this task - unauthenticated, which
the Gateway and every domain service already correctly reject with 401 on their own. This means
**every existing page's `apiClient.get/post/patch` call site became authenticated for free**, with
zero changes to any of those ~20 call sites. The browser never sees this header or the token -
confirmed directly against the real running app (see Testing).

## Logout

`GET /api/auth/logout`: deletes the Redis session record, clears the browser cookie
(`Max-Age=0`), and - if there was a real session - redirects through Keycloak's own RP-Initiated
Logout endpoint (`end_session_endpoint`, with `id_token_hint` and `post_logout_redirect_uri`) so
the user's Keycloak-side SSO session ends too, not just this app's local one.

## Local development behavior

- `Secure` cookie attribute is **off** (see above) - `next dev` serves plain HTTP.
- `KEYCLOAK_ISSUER`/`KEYCLOAK_CLIENT_ID`/`KEYCLOAK_CLIENT_SECRET`/`REDIS_URL` all default (in
  `lib/auth/config.ts`) to the exact values `docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md`
  documents - no `.env.local` is required to exercise this locally, as long as
  `docker compose -f infrastructure/docker/compose.yaml up -d` (Keycloak + Redis, at minimum) is
  running.
- Redirect URI is `http://localhost:3000/employees/api/auth/callback`, matching Keycloak's
  registered wildcard pattern `http://localhost:3000/employees/*` and `npm run dev`'s fixed port.

## Production differences

- `Secure` cookie attribute turns on automatically (`NODE_ENV=production`) - the deployment
  **must** terminate HTTPS in front of this app for cookies to actually be sent.
- `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `REDIS_URL` must all be
  supplied as real environment configuration - none of the local-dev defaults are safe to rely on
  in production, and `KEYCLOAK_CLIENT_SECRET` must never be committed anywhere.
- The redirect/logout URI registered in Keycloak must be narrowed from the local wildcard pattern
  to the exact production callback path(s) once the production domain is known.

## Known limitations (not implemented in this slice)

- **No "sign out everywhere" / device/session management** - only the one session tied to the
  cookie presented can be destroyed. A full device-management UI was explicitly out of scope.
- **No sliding-session-renewal policy beyond the refresh-token grant** - the 8-hour Redis TTL is a
  fixed backstop, not a configurable, renewing "remember me" policy.
- **Middleware performs presence-only cookie checks** - it deliberately does not look up Redis or
  verify the token (kept minimal per this task's own instruction); a present-but-dead session
  cookie is let through middleware and only caught by each page's own existing 401-handling
  (`AuthRequiredNotice`) once it actually calls the API client. This is why the root dashboard
  page can render a generic 200 shell for a dead session rather than a hard redirect - it already
  tolerated a failed personalization call before this task (`RootLayout`'s pre-existing
  try/catch), and this task did not change that page-level behavior.
- **No MFA UI, password reset, user registration, or admin Keycloak management** - explicitly out
  of scope; MFA itself is a Keycloak-side capability, independent of this BFF layer.
- **Discovery document/JWKS are cached in-process only**, for the life of the Next.js server
  process - acceptable for a single-instance local/early deployment; a multi-instance production
  deployment should confirm this caching behavior is still acceptable or add an explicit refresh
  policy.

## Testing

`npm test` (Vitest) - 26 tests across 4 files:

- `lib/auth/__tests__/pkce.test.ts`, `lib/auth/__tests__/cookie.test.ts` - pure unit tests (PKCE
  primitives, cookie attribute construction including the Secure/production decision).
- `middleware.test.ts` - the middleware function directly, for protected-vs-public path
  classification.
- `lib/auth/__tests__/integration.test.ts` - **end-to-end against the real local Keycloak and
  real Redis** (no fake Keycloak behavior anywhere in this file): boots a real `next dev` server
  plus a tiny local HTTP stub standing in for the API Gateway, then drives the actual
  Authorization Code + PKCE flow through Keycloak's real login form for multiple real test
  personas, covering unauthenticated state, the login route's real redirect parameters, a full
  real login, forged/missing/upstream-error callback rejection, cookie security attributes on the
  real Set-Cookie header, the bearer-token bridge (asserting the real stub received a real
  three-part JWT and the browser-facing response never contained it), logout, and
  expired/invalid-session handling. Requires
  `docker compose -f infrastructure/docker/compose.yaml up -d` (Keycloak + Redis) running first,
  and port 3000 free (the suite starts its own server there to match Keycloak's registered
  redirect URI).

Manually verified beyond the automated suite, against the **real** API Gateway and **real**
employee-service (not the stub) running against the real local Postgres, with
`gdb.security.oidc.issuer-uri`/`audience` pointed at this real Keycloak: an unauthenticated
request to `/api/v1/employees/me` through the Gateway returns 401; the same request bearing a
real Keycloak-issued access token for `employee.test` returns **404** ("No employee profile is
linked to this identity") - a business-logic result, not an authentication/authorization
rejection, proving both the Gateway and the domain service genuinely accepted the real token's
signature, issuer, and audience.
