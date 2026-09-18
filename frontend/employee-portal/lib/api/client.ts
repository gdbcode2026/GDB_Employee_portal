import { cookies } from "next/headers";

// Server-only: this module must never be imported from a Client Component. It reads the
// server-only GATEWAY_URL env var and forwards the incoming request's cookies manually,
// since a server-side fetch (unlike a browser fetch) never attaches them automatically.
// No token or cookie value is ever passed to client code.
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

async function forwardedCookieHeader(): Promise<string> {
  const store = await cookies();
  return store.getAll().map((cookie) => `${cookie.name}=${cookie.value}`).join("; ");
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const cookieHeader = await forwardedCookieHeader();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (cookieHeader) {
    headers.set("Cookie", cookieHeader);
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
