# Architecture Decisions

## Accepted directions

1. **Domain services behind an API Gateway.** This preserves bounded ownership and a controlled external surface.
2. **Private database ownership.** It avoids shared-schema coupling and permits independent migrations.
3. **REST plus RabbitMQ.** REST supports immediate user interactions; RabbitMQ propagates committed state changes without distributed transactions.
4. **Transactional outbox and idempotent consumers.** This is required for reliable event delivery across database and broker boundaries.
5. **OIDC/JWT with defense in depth.** The gateway validates tokens, while each service makes its own authorization decision.
6. **Docker Compose for local development.** It makes dependencies reproducible without prematurely operating Kubernetes.

## Deferred / must not implement yet

- Do not build services, fake business logic, sample payroll rules, or simulated approvals before discovery and contracts.
- Do not introduce Kubernetes, a service mesh, distributed tracing platform choice, or multi-region design without operational justification.
- Do not split into more services for announcements or support tickets until ownership, scale, and integration needs prove the boundary. Notification can initially deliver announcements; ticket ownership requires a decision.
- Do not expose direct service endpoints, share databases, place secrets in Compose files, or trust gateway authorization alone.
- Do not decide payroll, statutory tax, retention, or employee-data residency rules without accountable business/legal input.

## Open decisions

- OIDC provider and identity lifecycle source of truth.
- Existing website routing/deployment mechanism for `/employees`.
- Object-storage and malware-scanning providers.
- Approval delegation/escalation policy.
- Payroll geography and provider integration.
- Whether internal support tickets belong to an existing external system or a future GDB domain service.

