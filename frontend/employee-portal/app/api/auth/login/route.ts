import { NextRequest, NextResponse } from "next/server";
import { buildAuthorizationUrl } from "@/lib/auth/oidc";
import { deriveCodeChallenge, generateCodeVerifier, generateState } from "@/lib/auth/pkce";
import { storeAuthTransaction } from "@/lib/auth/session";
import { transactionCookieOptions } from "@/lib/auth/cookie";
import { AUTH_TRANSACTION_TTL_SECONDS, BASE_PATH } from "@/lib/auth/config";

/**
 * Starts the Authorization Code + PKCE flow. The browser only ever sees a 302 to Keycloak - the
 * PKCE code_verifier is generated here, server-side, and stored against `state` in Redis
 * (lib/auth/session.ts's storeAuthTransaction), never sent to or visible from the browser.
 *
 * Security review finding H-1 fix: also sets a short-lived transaction cookie carrying this same
 * `state`, scoped to the callback route only. The callback requires this cookie's value to match
 * the query-string `state` *in addition to* the Redis lookup - binding the authorization request
 * to the specific browser that started it, so a `state`/`code` pair captured from one browser's
 * login attempt cannot be relayed to and completed in a different (victim's) browser.
 */
export async function GET(request: NextRequest) {
  const state = generateState();
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = deriveCodeChallenge(codeVerifier);
  const redirectUri = `${request.nextUrl.origin}${BASE_PATH}/api/auth/callback`;

  await storeAuthTransaction(state, { codeVerifier, redirectUri, createdAt: Date.now() });

  const authorizationUrl = await buildAuthorizationUrl({ state, codeChallenge, redirectUri });
  const response = NextResponse.redirect(authorizationUrl);
  const cookie = transactionCookieOptions(AUTH_TRANSACTION_TTL_SECONDS);
  response.cookies.set(cookie.name, state, {
    httpOnly: cookie.httpOnly,
    sameSite: cookie.sameSite,
    path: cookie.path,
    secure: cookie.secure,
    maxAge: cookie.maxAgeSeconds,
  });
  return response;
}
