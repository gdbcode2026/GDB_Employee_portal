import { cookies } from "next/headers";
import { redisClient } from "@/lib/auth/redis";
import { refreshAccessToken, type TokenSet } from "@/lib/auth/oidc";
import {
  ACCESS_TOKEN_REFRESH_SKEW_SECONDS,
  AUTH_TRANSACTION_TTL_SECONDS,
  SESSION_COOKIE_NAME,
  SESSION_MAX_AGE_SECONDS,
} from "@/lib/auth/config";

const SESSION_KEY_PREFIX = "gdb:session:";
const TRANSACTION_KEY_PREFIX = "gdb:auth-txn:";

export interface AuthTransaction {
  codeVerifier: string;
  redirectUri: string;
  createdAt: number;
}

export interface SessionRecord {
  sub: string;
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  accessTokenExpiresAt: number;
  roles: string[];
  permissions: string[];
  createdAt: number;
}

/** Login step 1: remember the PKCE verifier against `state`, one-time, short-lived. Never exposed to the browser. */
export async function storeAuthTransaction(state: string, transaction: AuthTransaction): Promise<void> {
  await redisClient().set(TRANSACTION_KEY_PREFIX + state, JSON.stringify(transaction), "EX", AUTH_TRANSACTION_TTL_SECONDS);
}

/**
 * Callback step 1: a single atomic Redis GETDEL, not a separate GET-then-DEL - two concurrent
 * callback requests racing on the exact same `state` (e.g. a replayed/duplicated callback
 * request) can never both see a non-null result; at most one wins, every other one gets `null`,
 * by construction, not by a timing assumption.
 */
export async function consumeAuthTransaction(state: string): Promise<AuthTransaction | null> {
  const raw = await redisClient().getdel(TRANSACTION_KEY_PREFIX + state);
  return raw ? (JSON.parse(raw) as AuthTransaction) : null;
}

export async function createSession(record: SessionRecord): Promise<string> {
  const { generateSessionId } = await import("@/lib/auth/pkce");
  const sessionId = generateSessionId();
  await redisClient().set(SESSION_KEY_PREFIX + sessionId, JSON.stringify(record), "EX", SESSION_MAX_AGE_SECONDS);
  return sessionId;
}

export async function destroySession(sessionId: string): Promise<SessionRecord | null> {
  const key = SESSION_KEY_PREFIX + sessionId;
  const raw = await redisClient().get(key);
  await redisClient().del(key);
  return raw ? (JSON.parse(raw) as SessionRecord) : null;
}

function applyTokenSet(record: SessionRecord, tokens: TokenSet): SessionRecord {
  return {
    ...record,
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken ?? record.refreshToken,
    idToken: tokens.idToken ?? record.idToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
  };
}

/**
 * Reads the session, transparently refreshing the access token if it is expired or about to
 * expire (within ACCESS_TOKEN_REFRESH_SKEW_SECONDS) and a refresh token is available. Returns
 * `null` for a missing session, OR one whose access token is unusable and could not be refreshed
 * (expired/revoked refresh token, or Keycloak unreachable) - in every "returns null" case the
 * dead Redis record is also deleted, so the caller never needs to re-check.
 */
export async function getSession(sessionId: string): Promise<SessionRecord | null> {
  const key = SESSION_KEY_PREFIX + sessionId;
  const raw = await redisClient().get(key);
  if (!raw) return null;
  let record = JSON.parse(raw) as SessionRecord;

  const nowSeconds = Math.floor(Date.now() / 1000);
  const needsRefresh = record.accessTokenExpiresAt - nowSeconds <= ACCESS_TOKEN_REFRESH_SKEW_SECONDS;
  if (!needsRefresh) return record;

  if (!record.refreshToken) {
    await redisClient().del(key);
    return null;
  }

  try {
    const tokens = await refreshAccessToken(record.refreshToken);
    record = applyTokenSet(record, tokens);
    const ttl = await redisClient().ttl(key);
    await redisClient().set(key, JSON.stringify(record), "EX", ttl > 0 ? ttl : SESSION_MAX_AGE_SECONDS);
    return record;
  } catch {
    // Refresh token expired/revoked, or Keycloak unreachable - the session is no longer usable.
    // Fail closed: delete it rather than continuing to serve a stale/expired access token.
    await redisClient().del(key);
    return null;
  }
}

/**
 * Reads the session cookie for the current request and resolves its (possibly-just-refreshed)
 * record, or `null` if there is no session, it is invalid, or it could not be kept alive. Used
 * from Server Components, Server Actions, and Route Handlers alike (`cookies()` from
 * `next/headers` works in all three) - this is the single place that bridges "a cookie exists"
 * to "a usable access token exists," reused by both the session endpoint and the API client.
 */
export async function getCurrentSession(): Promise<SessionRecord | null> {
  const store = await cookies();
  const sessionId = store.get(SESSION_COOKIE_NAME)?.value;
  if (!sessionId) return null;
  return getSession(sessionId);
}
