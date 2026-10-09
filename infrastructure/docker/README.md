# Local platform dependencies

`docker compose -f infrastructure/docker/compose.yaml up -d` starts PostgreSQL, Redis, RabbitMQ, object storage, and Keycloak (GDB's approved OIDC provider), all bound to loopback only. The committed values are intentionally non-production development credentials, never production secrets. Each service has a distinct PostgreSQL database/user; no database is shared.

See `docs/security/KEYCLOAK_LOCAL_DEVELOPMENT.md` for Keycloak's realm/client/role/test-user setup and reset procedure.

