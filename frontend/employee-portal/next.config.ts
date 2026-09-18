import type { NextConfig } from "next";

// GATEWAY_URL is server-only (never NEXT_PUBLIC_): it is read here at request/build time
// by the Next.js server process, and by lib/api/client.ts for direct server-side calls.
// It is never bundled into browser JavaScript.
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  basePath: "/employees",
  output: "standalone",
  async rewrites() {
    // Keeps any future client-side fetch same-origin (see docs/architecture/ARCHITECTURE.md's
    // "/employees" coexistence design) so the gateway never needs CORS configuration.
    // Current pages fetch server-side directly against GATEWAY_URL and do not exercise this,
    // but it is required infrastructure for the documented same-origin routing pattern.
    return [
      {
        source: "/api/v1/:path*",
        destination: `${GATEWAY_URL}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
