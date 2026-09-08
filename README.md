# GDB Employee Portal

Architecture and delivery planning for the employee portal at `www.growdigitalbridge.com/employees`.

This repository currently contains **documentation only**. No application code, service scaffold, business logic, or production deployment manifests have been created.

## Proposed architecture

The portal is a Next.js frontend served under `/employees`, backed by an API Gateway and bounded Spring Boot services. Each service owns its PostgreSQL data and publishes durable domain events through RabbitMQ using the transactional-outbox pattern. Redis supports rate limiting, short-lived server-side state, and caching; it is not a system of record.

See [architecture documentation](docs/architecture/ARCHITECTURE.md), the [development roadmap](docs/DEVELOPMENT_ROADMAP.md), and [architecture decisions](docs/DECISIONS.md).

## Deliberately deferred

Kubernetes, service mesh, full-text search, data warehouse, payroll calculations, external HR/payroll integrations, and automated workflow rules are not included in the initial implementation scope. See [Decisions](docs/DECISIONS.md).

