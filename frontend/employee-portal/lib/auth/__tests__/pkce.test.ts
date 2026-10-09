import { describe, expect, it } from "vitest";
import { generateCodeVerifier, deriveCodeChallenge, generateState, generateSessionId } from "@/lib/auth/pkce";

describe("PKCE primitives", () => {
  it("generates URL-safe, non-predictable verifiers/states/session IDs of adequate length", () => {
    const values = [generateCodeVerifier(), generateState(), generateSessionId()];
    for (const value of values) {
      expect(value).toMatch(/^[A-Za-z0-9_-]+$/);
      expect(value.length).toBeGreaterThanOrEqual(32);
    }
    // Never two equal across independent calls (overwhelmingly improbable by chance - this is a
    // sanity check against an accidentally-deterministic implementation, not a security proof).
    expect(generateCodeVerifier()).not.toBe(generateCodeVerifier());
    expect(generateSessionId()).not.toBe(generateSessionId());
  });

  it("derives a deterministic S256 challenge from a given verifier, and never returns the verifier itself", () => {
    const verifier = "fixed-test-verifier-value-for-determinism-check";
    const challengeA = deriveCodeChallenge(verifier);
    const challengeB = deriveCodeChallenge(verifier);
    expect(challengeA).toBe(challengeB);
    expect(challengeA).not.toBe(verifier);
    expect(challengeA).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it("derives different challenges for different verifiers", () => {
    expect(deriveCodeChallenge("verifier-one")).not.toBe(deriveCodeChallenge("verifier-two"));
  });
});
