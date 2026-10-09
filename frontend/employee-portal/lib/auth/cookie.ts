import { BASE_PATH, OAUTH_CALLBACK_PATH, OAUTH_TRANSACTION_COOKIE_NAME, SESSION_COOKIE_NAME } from "@/lib/auth/config";

/**
 * Centralizes the one place this project decides whether `Secure` is set, so the production/
 * local-dev tradeoff is a single documented decision, not scattered conditionals. `Secure`
 * requires HTTPS; local development serves plain http://localhost, so a `Secure` cookie would
 * silently never be sent by the browser at all - this reads `NODE_ENV` (set to "production" by
 * `next build`/`next start`, "development" by `next dev`) exactly once, here, and is documented
 * in docs/security/NEXTJS_BFF_AUTHENTICATION.md's "local development behavior" / "production
 * differences" sections rather than decided silently.
 */
export function isProductionEnvironment(): boolean {
  return process.env.NODE_ENV === "production";
}

export interface SessionCookieOptions {
  name: string;
  value: string;
  maxAgeSeconds: number;
  httpOnly: boolean;
  sameSite: "lax";
  path: string;
  secure: boolean;
}

/**
 * Every cookie this app ever sets shares these three attributes (HttpOnly, SameSite=Lax,
 * environment-appropriate Secure) - only the name/path/maxAge differ. Building both this and
 * the transaction cookie through one shared function is what guarantees the "set" and "clear"
 * paths (and the session vs. transaction cookies) can never drift apart from each other.
 */
function cookieOptions(name: string, path: string, maxAgeSeconds: number): SessionCookieOptions {
  return {
    name,
    value: "",
    maxAgeSeconds,
    httpOnly: true,
    sameSite: "lax",
    path,
    secure: isProductionEnvironment(),
  };
}

export function sessionCookieOptions(maxAgeSeconds: number): SessionCookieOptions {
  return cookieOptions(SESSION_COOKIE_NAME, BASE_PATH, maxAgeSeconds);
}

/**
 * Security review finding H-1 fix. `Path` is deliberately scoped to the callback route alone
 * (not the whole app, unlike the session cookie) - the browser will only ever send this cookie
 * back on a request to that one route, minimizing its exposure to the narrowest possible surface.
 */
export function transactionCookieOptions(maxAgeSeconds: number): SessionCookieOptions {
  return cookieOptions(OAUTH_TRANSACTION_COOKIE_NAME, OAUTH_CALLBACK_PATH, maxAgeSeconds);
}
