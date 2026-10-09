// Server-only. All five values here are read once at module load; none is ever prefixed
// NEXT_PUBLIC_ and none is bundled into browser JavaScript. The three Keycloak values and
// REDIS_URL mirror docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md's local-dev values by default -
// production deployment must override every one of them via real environment configuration, the
// same convention GATEWAY_URL already uses in this file's sibling, lib/api/client.ts.

export const AUTH_CONFIG = {
  issuer: process.env.KEYCLOAK_ISSUER ?? "http://localhost:8180/realms/gdb",
  clientId: process.env.KEYCLOAK_CLIENT_ID ?? "gdb-employee-portal",
  // Local-dev-only default, matching docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md exactly.
  // Production MUST supply a real secret via KEYCLOAK_CLIENT_SECRET - never commit one.
  clientSecret: process.env.KEYCLOAK_CLIENT_SECRET ?? "local-development-only-client-secret",
  redisUrl: process.env.REDIS_URL ?? "redis://localhost:6379",
} as const;

/** basePath, duplicated from next.config.ts (not importable from a Route Handler/middleware context). */
export const BASE_PATH = "/employees";

export const SESSION_COOKIE_NAME = "gdb_session";

/**
 * Security review finding H-1 fix: binds the OAuth `state` to the specific browser that started
 * the login, so a `state`/`code` pair an attacker captured from their OWN login attempt cannot
 * be relayed to a victim's browser to complete there (login-CSRF / session-swapping). Scoped via
 * Path (see lib/auth/cookie.ts) to the callback route alone - it is never sent anywhere else,
 * including /login, /logout, or /session.
 */
export const OAUTH_TRANSACTION_COOKIE_NAME = "gdb_oauth_txn";

export const OAUTH_CALLBACK_PATH = `${BASE_PATH}/api/auth/callback`;

/** How long an auth transaction (state -> PKCE verifier) survives before the callback must complete. */
export const AUTH_TRANSACTION_TTL_SECONDS = 10 * 60;

/** Refresh an access token this many seconds before it actually expires, to avoid a request racing expiry. */
export const ACCESS_TOKEN_REFRESH_SKEW_SECONDS = 30;

/**
 * Absolute backstop on how long a server-side session record may live in Redis, independent of
 * the access/refresh token's own lifetime - the refresh-token grant (see lib/auth/session.ts) is
 * the primary mechanism that keeps a session usable; this is only a safety ceiling for an
 * internal employee portal (one working day), not a replacement for Keycloak's own session/
 * refresh-token lifetime policy.
 */
export const SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;
