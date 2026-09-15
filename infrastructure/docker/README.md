# Local platform dependencies

`docker compose -f infrastructure/docker/compose.yaml up -d` starts PostgreSQL, Redis, and RabbitMQ bound to loopback only. The committed values are intentionally non-production development credentials, never production secrets. Each service has a distinct PostgreSQL database/user; no database is shared.

