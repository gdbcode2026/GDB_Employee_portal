import { describe, expect, it, vi, afterEach } from "vitest";
import { sessionCookieOptions, isProductionEnvironment } from "@/lib/auth/cookie";

describe("sessionCookieOptions", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("is HttpOnly, SameSite=Lax, and scoped to /employees regardless of environment", () => {
    const options = sessionCookieOptions(3600);
    expect(options.httpOnly).toBe(true);
    expect(options.sameSite).toBe("lax");
    expect(options.path).toBe("/employees");
    expect(options.name).toBe("gdb_session");
    expect(options.maxAgeSeconds).toBe(3600);
  });

  it("is Secure in production", () => {
    vi.stubEnv("NODE_ENV", "production");
    expect(isProductionEnvironment()).toBe(true);
    expect(sessionCookieOptions(3600).secure).toBe(true);
  });

  it("documents Secure=false for local http development, not production", () => {
    vi.stubEnv("NODE_ENV", "development");
    expect(isProductionEnvironment()).toBe(false);
    expect(sessionCookieOptions(3600).secure).toBe(false);
  });

  it("the clearing cookie (maxAge 0) uses the exact same attributes as the setting cookie", () => {
    const setOptions = sessionCookieOptions(3600);
    const clearOptions = sessionCookieOptions(0);
    expect(clearOptions.httpOnly).toBe(setOptions.httpOnly);
    expect(clearOptions.sameSite).toBe(setOptions.sameSite);
    expect(clearOptions.path).toBe(setOptions.path);
    expect(clearOptions.secure).toBe(setOptions.secure);
    expect(clearOptions.maxAgeSeconds).toBe(0);
  });
});
