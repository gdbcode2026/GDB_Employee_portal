# Final Architecture Review

## Risks

- Prematurely implementing all sixteen services increases operational and delivery cost. Begin with the foundation and employee/organization core; keep Payroll calculation/sensitive business functionality and Reporting deferred. (Payroll's own technical foundation - period/run lifecycle scaffolding only, no calculation - is now implemented per `docs/PAYROLL_REQUIREMENTS.md`; this does not change the business-approval gate below.)
- Cross-domain manager authorization depends on timely, correct Organization relationships. Scope changes, delegation, and employee transfers require contract and cache-invalidation testing.
- Eventual consistency means projections, notifications, and workflows can lag. User interfaces must display authoritative source state and clear pending status.
- File storage, malware scanning, OIDC provider, payroll jurisdiction/provider, retention, and public-site edge routing are external dependencies not yet selected.

## Missing requirements and assumptions

GDB must provide leave rules, attendance sources/time zones, holiday ownership, approval/delegation/escalation policy, payroll legal/tax/provider requirements, reimbursement policy, document classification/retention, support-ticket ownership, data residency, RTO/RPO, observability/SIEM expectations, and accessibility/localization requirements. The design assumes one employee identity can be linked to an Employee record, Organization is authoritative for management scope, and configurable workflows—not hard-coded rules—govern approval.

## Decisions required from GDB

1. Existing-site hosting, reverse proxy/CDN, `/employees` and API routing, TLS and rollback ownership.
2. OIDC identity provider, local-login allowance, MFA/session/device policy, identity provisioning/deprovisioning source.
3. Object storage and malware scanning providers; data classification, retention, legal hold, backup and encryption-key policy.
4. Payroll jurisdiction, pay inputs, statutory rules, provider/payment integration, and permitted HR/Finance access.
5. Approval definitions, delegation/escalation/SLA behavior, attendance evidence, and support-ticket system boundary.
6. **Document Request** (WORKFLOWS.md's sixth supported Workflow request type, alongside Leave/WFH/Attendance Regularization/Expense/Asset Request) has no defined requirements: its purpose (access request, document generation, retention/legal-hold, or otherwise), required fields, requester eligibility, approver/approval authority, approved outcome, rejected outcome, owning service, database entity, API endpoint, and permission model are all undecided. This is DEFERRED — no `DocumentRequest` entity, API, permission, or `WORKFLOW_COMPLETED` consumer logic should be implemented until these are resolved. There is also an unresolved documentation conflict to settle alongside this decision: `COMMUNICATION.md`'s event contract table lists Document as a `WORKFLOW_COMPLETED` consumer, but `MICROSERVICES.md`'s own Document service row does not mention that consumption at all, and `REQUIREMENTS_TRACEABILITY.md`'s "Documents/policies" row lists neither Workflow as a co-owning module nor a request entity — unlike the parallel "Assets" row, which lists both. Whether Document Service consumes `WORKFLOW_COMPLETED` at all must be settled as part of this decision.

## Technical, security, and scalability debt

Without schema/API/event governance, separate services will drift; contract testing and version review are mandatory from Phase 1. Outbox/DLQ operations require monitoring and replay runbooks. Browser token storage, overly broad roles, stale organization scope, unscanned uploads, direct service exposure, shared credentials, and verbose sensitive logging are unacceptable security risks. Scale hotspots are gateway rate limiting, RabbitMQ consumers, document transfer, reporting projection rebuilds, and payroll batches; use measurements before adding Kubernetes, sharding, caches, or separate read systems.

## Recommended implementation order

1. Confirm the required GDB decisions and approve API/event/permission contracts.
2. Build platform baseline: repository standards, local Compose dependencies, gateway, OIDC integration, service template, audit/outbox/observability/security controls.
3. Build Employee and Organization with frontend portal shell, directory, profile, and resource authorization.
4. Build Workflow, Leave, Attendance/WFH/regularization, notifications, and associated audit/event tests.
5. Build Document/Asset/Expense, then Project/Performance after detailed product rules.
6. Payroll's technical foundation (period/run lifecycle scaffolding, RBAC, audit, idempotency) is implemented; implement Payroll calculation, payslips, payment/provider integration, and Reporting only after finance/reporting requirements and sensitive-data controls are approved.
7. Complete production readiness: operational runbooks, restore drills, load/security testing, staging route integration and rollback rehearsal.

