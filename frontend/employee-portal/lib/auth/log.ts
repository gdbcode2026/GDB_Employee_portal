/**
 * Security review hygiene fix: the browser never receives anything beyond a generic error code
 * (see app/api/auth/callback/route.ts) - whatever diagnostic detail operators actually need goes
 * here instead, server-side only (stdout, picked up by whatever log aggregation the deployment
 * already uses - no new logging infrastructure introduced).
 *
 * `detail` is a plain key/value record the caller builds explicitly, field by field - there is
 * no "log the whole object" call anywhere that could accidentally include a token/secret/code/
 * verifier by forgetting to strip one. Every call site in this codebase passes only category
 * labels, HTTP statuses, and non-sensitive identifiers (e.g. a redacted error type) - never a
 * raw access token, refresh token, ID token, authorization code, PKCE verifier, or client secret.
 */
export function logAuthEvent(event: string, detail: Record<string, string | number | boolean | null> = {}): void {
  console.error(JSON.stringify({ event, ...detail, timestamp: new Date().toISOString() }));
}
