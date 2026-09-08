# Security Architecture

## Authentication

Identity/Auth is the OIDC-compatible authorization server or integrates with a chosen external OIDC provider. The frontend uses Authorization Code flow with PKCE. Browser access tokens are short-lived; refresh-token/session handling must use secure, HttpOnly, SameSite cookies or a backend-for-frontend pattern, selected during implementation. APIs validate issuer, audience, signature, expiry, and scopes/JWT claims.

Internal calls use workload identity or OAuth2 client credentials; user context is propagated only when required for authorization and audit. Services do not trust a user identity merely because a request passed the gateway.

## Authorization and protection

Each resource-owning service enforces RBAC plus resource-level checks. Manager visibility comes from Organization-authoritative reporting relationships and is constrained to permitted teams. Payroll endpoints and storage are separately isolated, exposed only to Finance/authorized HR/Admin roles as policy permits.

Gateway controls include TLS termination, CORS allow-list, request-size limits, rate limiting via Redis, authentication pre-validation, route allow-listing, correlation IDs, and request logging that excludes sensitive values. It does not replace service authorization.

File upload requires type/size allow-lists, malware scanning before availability, private object storage, generated access URLs or streaming after authorization, and audit events. Secrets are never stored in repositories or images. Use a managed secret store in production.

Every security-sensitive action and access to sensitive records produces an immutable audit entry with actor, action, target, outcome, timestamp, correlation ID, and request source where appropriate.

## Detailed controls

The frontend uses OIDC Authorization Code with PKCE. Select GDB's issuer before implementation. JWTs must use asymmetric signatures; services validate issuer, audience, signature/JWKS, expiry, not-before, and scopes/roles. Access-token and refresh/session lifetimes, revocation, maximum session age, and device limit are configurable decisions; access tokens must be short-lived and refresh tokens rotated. Prefer a BFF/session design using Secure, HttpOnly, SameSite cookies. If browser bearer tokens are approved, never persist them in local storage and document XSS controls.

If local authentication is approved, use modern password hashing, a configurable minimum length/blocklist/history, reset expiry, throttling/lockout, and audit. The model is MFA-ready: enrollment, recovery, step-up claims and policy remain decisions. Sessions expose device metadata and support user/admin revocation.

Enforce exact CORS allow-lists, CSRF defense for cookie-authenticated mutations, TLS externally and between production workloads, request/identity rate limits in Redis, request-size/timeout limits, secure headers (HSTS, CSP, `X-Content-Type-Options`, frame/referrer controls), DTO validation/canonicalization, parameterized persistence, and safe problem responses. Store secrets only in local developer secret mechanisms and a production secret manager; encrypt databases, backups and object storage. Payroll gets separate database credentials/routes/logging/exports, with values redacted from telemetry.

Uploads require allow-listed type/size, content inspection, filename normalization, private object storage, malware scan quarantine, authorization before metadata/download, expiring signed access, and audit. Internal workload calls use narrowly scoped OAuth client credentials/workload identity. Fail closed for missing/invalid authorization, malware-scan failure, and unavailable sensitive policy/scope validation.
