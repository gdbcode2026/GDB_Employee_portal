# Architecture Decisions

## Accepted directions

1. **Domain services behind an API Gateway.** This preserves bounded ownership and a controlled external surface.
2. **Private database ownership.** It avoids shared-schema coupling and permits independent migrations.
3. **REST plus RabbitMQ.** REST supports immediate user interactions; RabbitMQ propagates committed state changes without distributed transactions.
4. **Transactional outbox and idempotent consumers.** This is required for reliable event delivery across database and broker boundaries.
5. **OIDC/JWT with defense in depth.** The gateway validates tokens, while each service makes its own authorization decision.
6. **Docker Compose for local development.** It makes dependencies reproducible without prematurely operating Kubernetes.

## Deferred / must not implement yet

- Do not build services, fake business logic, sample payroll rules, or simulated approvals before discovery and contracts. (Payroll's technical foundation and calculation *pipeline* - `PayrollPeriod`/`PayrollRun` lifecycle scaffolding, effective-dated compensation resolution, configuration-driven calculation/proration strategies, `PayrollRunLine`/`PayrollException` results, and attendance/leave event consumption - are implemented per `docs/PAYROLL_REQUIREMENTS.md`, with every calculation strategy resolving to a fixed, pre-configured amount or a zero adjustment: no salary/tax formula, no seeded pay-component catalogue *content*, and no invented rate or amount anywhere. HR/Finance management APIs for compensation, the pay-component catalogue, and a per-employee PF/ESI/Professional-Tax statutory profile are likewise implemented as pure structure - dates, status, identifiers, references - with no real salary amount, catalogue content, or statutory rate/threshold/eligibility rule introduced. This rule continues to block real statutory/tax logic and any pay-component catalogue content until GDB/Finance/Legal approval.)
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
- Document Request's purpose, ownership, database/API/permission model, and whether Document Service consumes `WORKFLOW_COMPLETED` — deferred pending resolution (see [Architecture Review](ARCHITECTURE_REVIEW.md) decision #6).

