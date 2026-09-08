# Development Rules

- Java 21, Spring Boot 3, constructor injection, immutable DTOs, package-by-domain, no static service locators. Spring Security is mandatory on every endpoint and method where appropriate; deny by default.
- REST uses `/api/v1`, DTOs rather than entities, RFC 9457 errors, validation annotations plus domain validation, idempotency for replay-prone writes, pagination, UTC/ISO-8601 timestamps, correlation IDs, and documented OpenAPI contracts. No leaking stack traces or persistence objects.
- PostgreSQL is owned per service. JPA mappings are explicit; no cross-service DB access, lazy serialization, or unbounded queries. Flyway is the only schema-change path; migrations are forward-only, reviewed, tested, and compatible with rolling deployment.
- RabbitMQ publishers use a transactional outbox; consumers are idempotent with inbox/processed IDs, bounded retries and DLQs. Event schemas are versioned and contain no secrets or unnecessary PII.
- Redis is cache/rate-limit/ephemeral state only; all keys get prefixes and TTLs. Never treat cache as authoritative.
- Docker images are minimal, non-root, pinned, vulnerability-scanned, configured by environment variables/secrets, and include health checks. Local Compose must use non-production credentials and no committed real secrets.
- Tests include unit, integration (PostgreSQL/RabbitMQ/Redis where relevant), contract, authorization negative-path, migration, and security regression tests. Critical workflows require end-to-end tests before release.
- Use structured logs with correlation IDs, metrics and traces; redact tokens, passwords, payroll data, PII and document content. Map exceptions centrally to safe problem responses.
- Validate all inputs and uploads; use parameterized access, output encoding, least privilege, dependency scanning, secret scanning, and code review for every change. Security-sensitive changes require security review.
- Git commits are focused and descriptive. PRs require tests, migration/event/API compatibility notes, docs updates, at least one reviewer, and no unrelated formatting churn.
- Next.js uses TypeScript strict mode, accessible responsive components, server/client boundaries deliberately chosen, typed API clients, no secrets in browser bundles, no raw token storage in local storage, guarded routes, safe error/loading states, and component/unit/e2e tests. Frontend authorization hints never replace API enforcement.
- Documentation is updated with behavior, API/event schema, migration, permission, operational, and decision changes. No fake business rules, payroll rules, fixtures, or unapproved integrations.

