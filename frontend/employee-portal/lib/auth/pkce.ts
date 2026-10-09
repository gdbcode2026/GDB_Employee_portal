import { randomBytes, createHash } from "crypto";

function base64url(input: Buffer): string {
  return input.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** RFC 7636 PKCE code_verifier: a cryptographically random, URL-safe string. */
export function generateCodeVerifier(): string {
  return base64url(randomBytes(32));
}

/** S256 code_challenge derived from a verifier - never the "plain" method. */
export function deriveCodeChallenge(verifier: string): string {
  return base64url(createHash("sha256").update(verifier).digest());
}

/** Opaque CSRF-protection value for the authorization request, unrelated to the session ID. */
export function generateState(): string {
  return base64url(randomBytes(24));
}

/** Opaque session identifier - never derived from, or reversible to, any token or user claim. */
export function generateSessionId(): string {
  return base64url(randomBytes(32));
}
