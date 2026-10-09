import { createRemoteJWKSet, jwtVerify, type JWTPayload } from "jose";
import { AUTH_CONFIG } from "@/lib/auth/config";

interface DiscoveryDocument {
  authorization_endpoint: string;
  token_endpoint: string;
  end_session_endpoint: string;
  jwks_uri: string;
}

let discoveryCache: DiscoveryDocument | null = null;
let jwksCache: ReturnType<typeof createRemoteJWKSet> | null = null;

export class OidcError extends Error {}

async function discovery(): Promise<DiscoveryDocument> {
  if (discoveryCache) return discoveryCache;
  const response = await fetch(`${AUTH_CONFIG.issuer}/.well-known/openid-configuration`, { cache: "no-store" });
  if (!response.ok) {
    throw new OidcError(`OIDC discovery failed with status ${response.status}`);
  }
  discoveryCache = (await response.json()) as DiscoveryDocument;
  return discoveryCache;
}

async function jwks() {
  if (!jwksCache) {
    const doc = await discovery();
    jwksCache = createRemoteJWKSet(new URL(doc.jwks_uri));
  }
  return jwksCache;
}

export async function buildAuthorizationUrl(params: {
  state: string;
  codeChallenge: string;
  redirectUri: string;
}): Promise<string> {
  const doc = await discovery();
  const url = new URL(doc.authorization_endpoint);
  url.searchParams.set("client_id", AUTH_CONFIG.clientId);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid");
  url.searchParams.set("redirect_uri", params.redirectUri);
  url.searchParams.set("state", params.state);
  url.searchParams.set("code_challenge", params.codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

export interface TokenSet {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  accessTokenExpiresAt: number;
}

async function tokenRequest(body: URLSearchParams): Promise<TokenSet> {
  const doc = await discovery();
  const response = await fetch(doc.token_endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
    cache: "no-store",
  });
  const text = await response.text();
  if (!response.ok) {
    throw new OidcError(`Token endpoint returned ${response.status}: ${text.slice(0, 300)}`);
  }
  const json = JSON.parse(text) as {
    access_token?: string;
    refresh_token?: string;
    id_token?: string;
    expires_in?: number;
  };
  if (!json.access_token || typeof json.expires_in !== "number") {
    throw new OidcError("Token response missing access_token/expires_in");
  }
  return {
    accessToken: json.access_token,
    refreshToken: json.refresh_token ?? null,
    idToken: json.id_token ?? null,
    accessTokenExpiresAt: Math.floor(Date.now() / 1000) + json.expires_in,
  };
}

/** Step: exchange an authorization code (with its PKCE verifier) for a real token set. Server-side only. */
export function exchangeCodeForTokens(code: string, codeVerifier: string, redirectUri: string): Promise<TokenSet> {
  return tokenRequest(
    new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: redirectUri,
      client_id: AUTH_CONFIG.clientId,
      client_secret: AUTH_CONFIG.clientSecret,
      code_verifier: codeVerifier,
    }),
  );
}

/** Step: exchange a refresh token for a new access token, so an expiring session need not re-login. */
export function refreshAccessToken(refreshToken: string): Promise<TokenSet> {
  return tokenRequest(
    new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: refreshToken,
      client_id: AUTH_CONFIG.clientId,
      client_secret: AUTH_CONFIG.clientSecret,
    }),
  );
}

/**
 * Verifies a Keycloak-issued access token exactly the way the backend already does (same three
 * checks: signature via JWKS, issuer, audience) - not a bespoke/weaker scheme. Throws on any
 * failure; callers must treat that as "this token/session is not valid," never fall back to
 * trusting the token unverified.
 */
export async function verifyAccessToken(token: string): Promise<JWTPayload> {
  const keySet = await jwks();
  const { payload } = await jwtVerify(token, keySet, {
    issuer: AUTH_CONFIG.issuer,
    audience: AUTH_CONFIG.clientId,
  });
  return payload;
}

export async function buildEndSessionUrl(params: {
  idTokenHint: string | null;
  postLogoutRedirectUri: string;
}): Promise<string> {
  const doc = await discovery();
  const url = new URL(doc.end_session_endpoint);
  url.searchParams.set("client_id", AUTH_CONFIG.clientId);
  url.searchParams.set("post_logout_redirect_uri", params.postLogoutRedirectUri);
  if (params.idTokenHint) {
    url.searchParams.set("id_token_hint", params.idTokenHint);
  }
  return url.toString();
}
