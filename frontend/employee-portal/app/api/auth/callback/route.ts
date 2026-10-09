import { NextRequest, NextResponse } from "next/server";
import { exchangeCodeForTokens, verifyAccessToken } from "@/lib/auth/oidc";
import { consumeAuthTransaction, createSession } from "@/lib/auth/session";
import { sessionCookieOptions, transactionCookieOptions } from "@/lib/auth/cookie";
import { BASE_PATH, OAUTH_TRANSACTION_COOKIE_NAME, SESSION_MAX_AGE_SECONDS } from "@/lib/auth/config";
import { logAuthEvent } from "@/lib/auth/log";

function stringArray(value: unknown): string[] {
  return Array.isArray(value) && value.every((v) => typeof v === "string") ? value : [];
}

/** Clears the transaction cookie on a response, regardless of outcome - it is one-time-use by design and must never survive past this one request. */
function clearTransactionCookie(response: NextResponse): void {
  const cookie = transactionCookieOptions(0);
  response.cookies.set(cookie.name, "", {
    httpOnly: cookie.httpOnly,
    sameSite: cookie.sameSite,
    path: cookie.path,
    secure: cookie.secure,
    maxAge: 0,
  });
}

/**
 * Every failure path returns the exact same generic body/status to the browser - the specific
 * reason is only ever visible server-side, via `logAuthEvent` (security review finding M-4 fix:
 * previously this echoed Keycloak's/jose's raw error text back to the caller). `detail` here is
 * built field-by-field at each call site below - never a raw token/code/verifier/secret.
 */
function authenticationFailed(event: string, detail: Record<string, string | number | boolean | null> = {}): NextResponse {
  logAuthEvent(event, detail);
  const response = NextResponse.json({ error: "authentication_failed" }, { status: 400 });
  clearTransactionCookie(response);
  return response;
}

/**
 * Receives Keycloak's redirect back. Every failure mode below (upstream error, missing params,
 * missing/mismatched transaction cookie, invalid/expired/already-consumed state, failed code
 * exchange, failed token verification) returns the same generic error - never a fake success,
 * and never Keycloak's/jose's raw internal error text.
 *
 * Security review finding H-1 fix: `state` must match *both* the one-time Redis transaction
 * (unchanged from before) *and* the `gdb_oauth_txn` cookie set by /login in this same browser.
 * An attacker who captures a `state`/`code` pair from their own login attempt and relays it to a
 * victim's browser cannot satisfy this second check - the victim's browser was never issued a
 * transaction cookie for that specific `state` (it only ever receives one when it itself calls
 * /login), so the authorization request is never completable in any browser other than the one
 * that started it.
 */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const upstreamError = params.get("error");
  if (upstreamError) {
    return authenticationFailed("oauth_callback_failed", { reason: "upstream_error", upstreamError });
  }

  const state = params.get("state");
  const code = params.get("code");
  if (!state || !code) {
    return authenticationFailed("oauth_callback_failed", { reason: "missing_callback_parameters" });
  }

  const transactionCookie = request.cookies.get(OAUTH_TRANSACTION_COOKIE_NAME)?.value;
  if (!transactionCookie) {
    return authenticationFailed("oauth_callback_failed", { reason: "missing_transaction_cookie" });
  }
  if (transactionCookie !== state) {
    return authenticationFailed("oauth_callback_failed", { reason: "state_cookie_mismatch" });
  }

  // One-time, atomic use (lib/auth/session.ts's consumeAuthTransaction is a single Redis GETDEL):
  // a replayed, forged, expired, or already-consumed `state` fails here, every time, by
  // construction - there is nothing left in Redis to consume a second time, and nothing
  // resembling the original PKCE verifier can be guessed from the outside.
  const transaction = await consumeAuthTransaction(state);
  if (!transaction) {
    return authenticationFailed("oauth_callback_failed", { reason: "invalid_or_expired_state" });
  }

  let tokens;
  try {
    tokens = await exchangeCodeForTokens(code, transaction.codeVerifier, transaction.redirectUri);
  } catch (error) {
    return authenticationFailed("oauth_callback_failed", {
      reason: "token_exchange_failed",
      errorType: error instanceof Error ? error.constructor.name : "unknown",
    });
  }

  let claims;
  try {
    claims = await verifyAccessToken(tokens.accessToken);
  } catch (error) {
    return authenticationFailed("oauth_callback_failed", {
      reason: "token_validation_failed",
      errorType: error instanceof Error ? error.constructor.name : "unknown",
    });
  }

  if (!claims.sub) {
    return authenticationFailed("oauth_callback_failed", { reason: "token_missing_subject" });
  }

  const sessionId = await createSession({
    sub: claims.sub,
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    idToken: tokens.idToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    roles: stringArray(claims.roles),
    permissions: stringArray(claims.permissions),
    createdAt: Date.now(),
  });

  const response = NextResponse.redirect(new URL(`${BASE_PATH}/`, request.nextUrl.origin));
  clearTransactionCookie(response);
  const cookie = sessionCookieOptions(SESSION_MAX_AGE_SECONDS);
  response.cookies.set(cookie.name, sessionId, {
    httpOnly: cookie.httpOnly,
    sameSite: cookie.sameSite,
    path: cookie.path,
    secure: cookie.secure,
    maxAge: cookie.maxAgeSeconds,
  });
  return response;
}
