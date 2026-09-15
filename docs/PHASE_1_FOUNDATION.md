# Phase 1 Platform Foundation

Maven was selected for the Java multi-module build because it is already available in the development environment and provides conventional Spring Boot dependency/BOM management. The project target remains Java 21; local JDK 17 cannot build it and must not cause a target downgrade. OIDC issuer/audience are deliberately absent from committed configuration; deployment supplies them only after GDB selects a provider.

Implemented foundations: `api-gateway`, `audit-service`, `notification-service`, and `platform-common`. No business-domain services or business logic exist. The gateway exposes only Actuator health/info without authentication and denies all other requests until GDB selects an OIDC provider; gateway routes are declared for the two foundation services but remain denied by the edge security baseline. Audit and notification endpoints are deliberately unavailable/denied pending authorization and product decisions.

Local Compose starts PostgreSQL with distinct audit/notification databases and users, Redis only for future cache/rate-limit/ephemeral state, and RabbitMQ. The audit schema includes append-only audit records, outbox, and processed-event foundations. RabbitMQ uses durable `gdb.domain.events`, a service queue, and a dead-letter exchange convention. The Next.js shell is mounted at `/employees`, contains no login and stores no token.

The compose credentials are fixed, explicitly non-production local-development values. Real credentials belong in local untracked configuration and production secret management. Before Phase 2, choose OIDC issuer/audience/session design, site reverse-proxy routing, and gateway routes for newly approved services.
