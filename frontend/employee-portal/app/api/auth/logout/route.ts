import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { buildEndSessionUrl } from "@/lib/auth/oidc";
import { destroySession } from "@/lib/auth/session";
import { BASE_PATH, SESSION_COOKIE_NAME } from "@/lib/auth/config";
import { sessionCookieOptions } from "@/lib/auth/cookie";

/**
 * Logout always: (1) deletes the local Redis session record, (2) clears the browser's session
 * cookie, (3) if there was a real session, also invokes Keycloak's own RP-Initiated Logout so
 * the user's Keycloak-side SSO session ends too, not just this app's local one.
 */
export async function GET(request: NextRequest) {
  const store = await cookies();
  const sessionId = store.get(SESSION_COOKIE_NAME)?.value;
  const record = sessionId ? await destroySession(sessionId) : null;

  const postLogoutRedirectUri = `${request.nextUrl.origin}${BASE_PATH}/`;
  const destination = record
    ? await buildEndSessionUrl({ idTokenHint: record.idToken, postLogoutRedirectUri })
    : postLogoutRedirectUri;

  const response = NextResponse.redirect(destination);
  const cookie = sessionCookieOptions(0);
  response.cookies.set(cookie.name, "", {
    httpOnly: cookie.httpOnly,
    sameSite: cookie.sameSite,
    path: cookie.path,
    secure: cookie.secure,
    maxAge: 0,
  });
  return response;
}
