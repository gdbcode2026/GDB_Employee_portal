import { getCurrentSession } from "@/lib/auth/session";

// Server-only: this module must never be imported from a Client Component. It reads the
// server-only GATEWAY_URL env var. No token or cookie value is ever passed to client code.
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080";

interface ProblemDetail {
  title?: string;
  detail?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly title?: string;
  readonly detail?: string;

  constructor(status: number, title?: string, detail?: string) {
    super(detail ?? title ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.title = title;
    this.detail = detail;
  }
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  // Security review hygiene fix (M-3): this request no longer forwards the browser's own
  // gdb_session/gdb_oauth_txn cookies to the Gateway - confirmed nothing downstream ever reads
  // a cookie (the Gateway and every domain service authenticate via Authorization: Bearer only),
  // so forwarding them was unnecessary propagation of a credential-adjacent value into
  // Gateway/service-side logs. The real, required mechanism is the Bearer-token bridge below.
  //
  // The BFF token bridge: the browser never sees this header or the token it carries - it is
  // looked up server-side, per request, from the opaque session cookie (lib/auth/session.ts),
  // and attached here so every existing apiClient.get/post/patch call site becomes authenticated
  // "for free" once a session exists, with zero changes needed at any of those call sites. With
  // no session (or an invalid/expired one that could not be refreshed), the request proceeds
  // exactly as it already did before this bridge existed - unauthenticated, which the gateway
  // and every domain service already correctly reject with 401 on their own.
  if (!headers.has("Authorization")) {
    const session = await getCurrentSession();
    if (session) {
      headers.set("Authorization", `Bearer ${session.accessToken}`);
    }
  }

  const response = await fetch(`${GATEWAY_URL}${path}`, { ...init, headers, cache: "no-store" });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const body: unknown = text ? safeJsonParse(text) : undefined;

  if (!response.ok) {
    const problem = (body ?? {}) as ProblemDetail;
    throw new ApiError(response.status, problem.title, problem.detail);
  }

  return body as T;
}

export const apiClient = {
  get: <T>(path: string): Promise<T> => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body: unknown): Promise<T> =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown): Promise<T> =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
};
