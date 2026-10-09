import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["**/*.test.ts"],
    // The integration suite drives a real `next dev` server (first-compile latency per route in
    // a fresh process) plus real Keycloak HTTP round-trips; the default 5s is too tight for that,
    // not a sign anything is actually hanging.
    testTimeout: 30_000,
  },
  resolve: {
    alias: {
      "@": import.meta.dirname,
    },
  },
});
