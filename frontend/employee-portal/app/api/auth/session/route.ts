import { NextResponse } from "next/server";
import { getCurrentSession } from "@/lib/auth/session";

/**
 * The ONLY browser-facing view of authentication state. Deliberately returns 200 with
 * `authenticated: false` for a missing/invalid session, never a 401 - this is a status query,
 * not a protected resource. Never includes accessToken/refreshToken/idToken/client_secret/PKCE
 * material/any session-internal field - UI authorization built on `roles`/`permissions` here is
 * for showing/hiding navigation only; the backend remains the sole authorization authority.
 */
export async function GET() {
  const session = await getCurrentSession();
  if (!session) {
    return NextResponse.json({ authenticated: false });
  }
  return NextResponse.json({
    authenticated: true,
    subject: session.sub,
    roles: session.roles,
    permissions: session.permissions,
  });
}
