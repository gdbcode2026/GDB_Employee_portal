import Redis from "ioredis";
import { AUTH_CONFIG } from "@/lib/auth/config";

// Reuses the Redis instance already running in infrastructure/docker/compose.yaml (the gateway
// already depends on it for rate limiting, per docs/security/SECURITY.md) rather than inventing
// a new permanent datastore solely for session state - exactly the smallest-footprint option the
// task asked to evaluate first. A single lazily-connected client is reused across requests
// (Next.js keeps server modules warm between requests in the same process), matching how
// lib/api/client.ts already treats GATEWAY_URL as a module-level constant.
let client: Redis | null = null;

export function redisClient(): Redis {
  if (!client) {
    client = new Redis(AUTH_CONFIG.redisUrl, { lazyConnect: true, maxRetriesPerRequest: 2 });
  }
  return client;
}
