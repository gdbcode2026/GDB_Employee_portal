# Keycloak Local Development

**Keycloak is GDB's approved OIDC identity provider** (OIDC Identity Provider Decision, approved;
self-hosted). This document covers **local development only**. No frontend login, BFF, callback,
logout, or session-cookie code exists yet — this task only stands up the provider-side pieces a
future BFF will need. Nothing here is production configuration; every credential below is a
clearly-fake, committed, local-development-only value, exactly like every other service in
`infrastructure/docker/compose.yaml`.

**Do not reuse any credential on this page in staging or production.**

## Starting Keycloak locally

```
docker compose -f infrastructure/docker/compose.yaml up -d
```

This starts Keycloak alongside Postgres/Redis/RabbitMQ/object-storage, bound to `127.0.0.1` only.
Keycloak depends on Postgres being healthy first, then imports the realm below on its first boot
against an empty `keycloak_db`. Startup (including the realm import) takes roughly 30-60 seconds
the first time; subsequent restarts are faster (~15-20s) since the database schema migration is
already applied.

Check health:

```
docker inspect --format='{{.State.Health.Status}}' gdb-platform-local-keycloak-1
```

## Resetting the local realm

**A plain container restart does NOT re-apply the realm JSON.** Keycloak only performs a full
`OVERWRITE_EXISTING` import against a genuinely empty `keycloak_db` — if the realm already exists
in the database, `--import-realm` logs `Strategy: IGNORE_EXISTING` and skips the import entirely
(confirmed empirically, not assumed). To safely recreate the local Keycloak environment **without
touching any domain-service database** (Keycloak's dedicated database lives inside the same
shared Postgres container, but in its own database/user - `keycloak_db`/`gdb_keycloak` - never
`employee_db`, `payroll_db`, or any other domain database):

```
docker compose -f infrastructure/docker/compose.yaml stop keycloak
docker exec gdb-platform-local-postgres-1 psql -U gdb_local -d platform_admin \
  -c "DROP DATABASE IF EXISTS keycloak_db;" \
  -c "DROP USER IF EXISTS gdb_keycloak;" \
  -c "CREATE USER gdb_keycloak WITH PASSWORD 'local-development-only';" \
  -c "CREATE DATABASE keycloak_db OWNER gdb_keycloak;"
docker compose -f infrastructure/docker/compose.yaml start keycloak
```

On a genuinely fresh checkout (first-ever `docker compose up`, no pre-existing `postgres-data`
volume), `infrastructure/docker/postgres/init-databases.sql` creates `keycloak_db`/`gdb_keycloak`
automatically on Postgres's first boot and none of the above is needed.

## Local URLs

| Item | Value |
|---|---|
| Keycloak base URL | `http://localhost:8180` |
| Realm | `gdb` |
| Issuer (`iss`) | `http://localhost:8180/realms/gdb` |
| Discovery document | `http://localhost:8180/realms/gdb/.well-known/openid-configuration` |
| Authorization endpoint | `http://localhost:8180/realms/gdb/protocol/openid-connect/auth` |
| Token endpoint | `http://localhost:8180/realms/gdb/protocol/openid-connect/token` |
| JWKS | `http://localhost:8180/realms/gdb/protocol/openid-connect/certs` |
| End-session (logout) endpoint | `http://localhost:8180/realms/gdb/protocol/openid-connect/logout` |
| Admin console | `http://localhost:8180/admin/master/console/` (login: `gdb_local` / `local-development-only`) |
| Client ID | `gdb-employee-portal` |
| Client secret (local dev only) | `local-development-only-client-secret` |
| Redirect URI (registered pattern) | `http://localhost:3000/employees/*` |
| Post-logout redirect URI (registered pattern) | `http://localhost:3000/employees/*` |

The redirect/logout URI is registered as a **wildcard prefix**, not a single literal path, because
the exact BFF callback/logout route doesn't exist yet. Narrow this to the exact literal path(s)
once the BFF is implemented (next task).

These values are consistent with the existing Next.js dev setup: `next dev --port 3000` plus
`next.config.ts`'s `basePath: "/employees"`.

## Realm/client configuration

Defined declaratively in `infrastructure/docker/keycloak/import/gdb-realm.json`, imported on
first boot. Generated (not hand-written) to guarantee an exact, duplicate-free match against
`docs/security/RBAC.md`'s permission catalogue - see that file's own header comment for the exact
rules used (one documented interpretation, one documented exclusion, both explained there).

- **Client**: `gdb-employee-portal` - confidential (has a secret), **Authorization Code only**
  (`standardFlowEnabled: true`), **implicit flow disabled**, **direct access grants (password
  grant) disabled**, service accounts disabled, **PKCE `S256` enforced**
  (`pkce.code.challenge.method: S256`). This is provider-side metadata only - no BFF code exists
  yet to use it.
- **Audience**: a dedicated `oidc-audience-mapper` protocol mapper adds `gdb-employee-portal` to
  the `aud` claim. Without this, Keycloak's access tokens default to `aud: "account"`, which would
  fail every backend service's existing audience validator - this was verified directly against a
  real issued token, not assumed.

## Roles (realm roles)

Exactly the six from `docs/security/RBAC.md`, no more, no fewer: `EMPLOYEE`, `MANAGER`, `HR`,
`FINANCE`, `ADMIN`, `SUPER_ADMIN`. Each test user below holds exactly one.

## Permissions (client roles on `gdb-employee-portal`)

The full 77-entry permission catalogue from `docs/security/RBAC.md`'s permission-catalogue line,
modeled as Keycloak **client roles** scoped to `gdb-employee-portal` (not realm roles) - chosen
because the backend's JWT authority converter (identical across the gateway and all 10 domain
services, e.g. `backend/api-gateway/.../config/SecurityConfig.java`) reads `permissions` and
`roles` as two independent flat-string-array claims with no structural distinction between them;
Keycloak's own idiomatic convention reserves **realm roles** for cross-cutting identity (the six
RBAC roles) and **client roles** for capabilities scoped to one application (the permission
catalogue) - this is a deliberate design choice, not a guess, made because either Keycloak
primitive could technically produce the required flat claim shape.

Two deliberate, documented decisions versus RBAC.md's literal text:

1. `notification.read.self/manage` is expanded to `notification.read.self` + `notification.manage`
   (matching the `<domain>.manage` sibling-capability pattern every other entry in the same
   catalogue line uses - `organization.manage`, `project.manage`, `document.manage`, etc. - rather
   than an invented nested `notification.read.manage` form nothing else uses).
2. `workload.document.upload` (document-service's service-to-service, client-credentials-only
   authority, per that service's own `SecurityConfig` Javadoc) is **excluded** - it was never a
   human/RBAC.md permission to begin with, so excluding it is not "removing an existing
   permission."

## Test users (local development only)

All six use the password `local-development-only` - never a real credential, obviously fake,
**never to be reused in staging or production**. Each holds exactly one realm role and only the
client-role permissions that role's row in `docs/security/RBAC.md` actually describes - no user
holds more than its documented role, and `SUPER_ADMIN` is not granted to anyone else for
convenience.

| Username | Role | Permission count | Basis |
|---|---|---|---|
| `employee.test` | `EMPLOYEE` | 26 | RBAC.md's Employee row (baseline self-service only) |
| `manager.test` | `MANAGER` | 41 | Employee baseline + RBAC.md's Manager row (team-scoped grants) |
| `hr.test` | `HR` | 40 | Employee baseline + RBAC.md's HR row. **Excludes every `payroll.*` permission** - RBAC.md states "Payroll needs separately granted payroll permission," so HR does not get one by default. |
| `finance.test` | `FINANCE` | 33 | Employee baseline + RBAC.md's Finance row (`payroll.read.all`, `payroll.process`, `payslip.read.all`, expense all/approve/reimburse, finance reporting). **Excludes `payroll.approve`** - see below. |
| `admin.test` | `ADMIN` | 29 | Employee baseline + RBAC.md's Admin row (`identity.manage`, `role.manage`, `system.admin` only - explicitly "no automatic HR or payroll data access"). |
| `superadmin.test` | `SUPER_ADMIN` | 77 (all) | RBAC.md: "All permissions, including... `breakglass.use`." |

**Why neither `hr.test` nor `finance.test` holds `payroll.approve`**: `docs/security/RBAC.md`'s
own text states the payroll maker/checker split (which of HR/Finance gets `payroll.process` vs.
`payroll.approve`) is **`PENDING_GDB_APPROVAL`** - not yet decided. Granting `payroll.approve` to
either test persona here would pre-empt that undecided split. `superadmin.test` holds it (and
every other permission), so the capability remains fully testable without guessing GDB's answer.

## Token claims (verified against a real issued token)

Verified by running the actual Authorization Code + PKCE flow end-to-end (scripted - PKCE
verifier/challenge, real login form submission, real code exchange) against each of the six test
users and decoding the resulting access token. A representative decoded token
(`employee.test`, truncated):

```json
{
  "exp": 1791453914,
  "iat": 1791453614,
  "iss": "http://localhost:8180/realms/gdb",
  "aud": "gdb-employee-portal",
  "sub": "85e88149-2a63-4a86-b5b8-64b7e786f38a",
  "permissions": ["employee.read.self", "employee.update.self", "..."],
  "roles": ["EMPLOYEE"]
}
```

| Claim | Present | Notes |
|---|---|---|
| `iss` | Yes | Exactly `http://localhost:8180/realms/gdb` |
| `aud` | Yes | `gdb-employee-portal`, via the dedicated audience mapper |
| `sub` | Yes | Keycloak's internal user UUID |
| `exp` | Yes | |
| `iat` | Yes | |
| `nbf` | **No** | Keycloak 26's default access token does not emit `nbf` at all for this realm/client - confirmed empirically, not assumed. **This does not break existing backend validation**: Spring Security's `JwtValidators.createDefaultWithIssuer(...)` (used unchanged by every service) only validates `nbf` if the claim is present; its absence is not a failure. If GDB wants `nbf` present for completeness, that needs an explicit realm "not before" policy or an additional protocol mapper - a decision for a future task, not implemented here. |
| `roles` | Yes | Exactly the one expected role string per user, no `ROLE_` prefix (added by Spring code, not expected in the token) |
| `permissions` | Yes | Exact expected set per user (verified element-for-element for `employee.test`, verified by count for all six) |

Every service's existing JWT authority converter is unchanged by this task - it already expects
exactly this shape.

## No changes to application code

This task touched only `infrastructure/docker/`. No frontend, API Gateway, domain-service, or
RBAC code was modified. `gdb.security.oidc.issuer-uri`/`audience` are still unset in every
service's configuration - wiring a real service to this local Keycloak instance (setting those
two properties to `http://localhost:8180/realms/gdb` and `gdb-employee-portal` respectively) is
explicitly out of scope for this task.
