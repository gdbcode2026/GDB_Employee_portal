# Microservices

| Service | Responsibility | Owns |
|---|---|---|
| API Gateway | Public API entry point, route enforcement, edge controls | Route/rate-limit configuration only |
| Identity/Auth | OIDC-compatible identities, credentials/federation, token issuance, role assignments | identities, credentials metadata, sessions, clients, role grants |
| Employee | Employee lifecycle and personal/employment profile | employees, contacts, employment records, manager references |
| Organization | Organizational structure and directory hierarchy | departments, teams, positions, reporting relationships, locations |
| Attendance | Time records and attendance actions | check-ins, shifts, attendance records, regularization requests, WFH requests, holidays |
| Leave | Leave balances, requests, approvals and policy references | leave types, balances, leave requests, approval state |
| Payroll | Payroll period processing and payslip access metadata | pay components, payroll runs, payslip metadata; sensitive isolation |
| Expense | Expense claims, receipts metadata, reimbursement approval state | claims, line items, approvals, reimbursement status |
| Project | Projects, memberships and tasks | projects, project memberships, tasks, assignments |
| Performance | Goals, review cycles and evaluations | goals, review cycles, reviews, ratings |
| Document | Secure file metadata/access and policy publication | document metadata, versions, classifications, policy records |
| Asset | Assigned company assets and lifecycle | assets, assignments, returns, condition records |
| Workflow | Reusable approval orchestration; not domain ownership | workflow definitions, instances, approval tasks |
| Notification | Delivery preferences and notification delivery | preferences, templates, delivery attempts |
| Audit | Immutable security/business audit trail | audit entries, retention metadata |
| Reporting | Read-optimized cross-domain reports | report definitions, projections, generated report metadata |

## Boundary rules

An employee identifier is referenced across domains but its profile is owned only by Employee. Organization owns the manager/team relationship used for team-scoped access. Workflow coordinates approvals but does not own leave, expense, or attendance records. Document owns metadata and access decisions; binary files reside in private object storage. Reporting is read-only and never becomes a transactional source of truth.

## Detailed service contracts

All owning services are Spring Boot resource servers. They validate authorization, own their migrations/outbox, and never depend on another service database. REST dependencies are authenticated, time-bounded, and only for immediate authoritative needs.

| Service | APIs exposed / consumed | Events published / consumed | Must not own; security and failure considerations |
|---|---|---|---|
| API Gateway | Exposes public `/api/v1/**`; consumes OIDC discovery/JWKS | No domain events | Owns neither business data nor orchestration. Validates token/rate limits and fails closed for protected paths. |
| Identity/Auth | OIDC/OAuth, identity/role/session APIs; may consume Employee lifecycle | Identity lifecycle; consumes `EMPLOYEE_CREATED/DEACTIVATED` when provisioning is approved | No HR profile. Strong credential controls; already-valid signed tokens remain verifiable during issuer outage. |
| Employee | Profile/lifecycle APIs; may consume Organization scope API | `EMPLOYEE_CREATED/UPDATED/DEACTIVATED`; consumes workflow result if needed | No team hierarchy, credentials, payroll. PII read/write audited; outbox protects lifecycle delivery. |
| Organization | Directory/hierarchy/team-scope APIs; consumes Employee display reference API/projection | Organization relationship changes; consumes employee lifecycle | No employment profile. Reject reporting cycles and treat stale scope conservatively. |
| Attendance | Attendance/WFH/regularization APIs; consumes Organization scope | `ATTENDANCE_FINALIZED`, regularization outcome; consumes workflow completion | No leave balance/payroll. Timezone, duplicates, and finalization concurrency handled explicitly. |
| Leave | Balance/request APIs; consumes Organization scope and workflow completion | `LEAVE_REQUESTED/APPROVED/REJECTED` | No workflow-task truth. Concurrency-safe balance reservation required. |
| Payroll (deferred) | Payslip/run APIs; consumes final inputs under explicit contracts | `PAYROLL_PROCESSED` | No unsupplied tax rules. Separate routes/credentials/logging; fail closed. |
| Expense | Claim APIs; consumes document references/workflow completion | `EXPENSE_SUBMITTED/APPROVED` | No file bytes/payment execution. Duplicate-claim and receipt access controls required. |
| Project | Project/task APIs; consumes employee/organization references | Deferred project events | No performance ratings. Membership/assignment determines scope. |
| Performance | Goal/review APIs; consumes Organization scope | Deferred completion events | No employment master data. Reviews restricted and audited. |
| Document | Upload/metadata/authorized-download APIs | `DOCUMENT_UPLOADED`; consumes lifecycle cleanup | No binary files in database. Scan gate/private object storage; object-store failure is handled safely. |
| Asset | Asset/assignment/request APIs; consumes workflow completion | `ASSET_ASSIGNED`; consumes lifecycle | No procurement/accounting. Custody transitions audited/idempotent. |
| Workflow | Start/decision/cancel/task APIs; consumes request events | `WORKFLOW_COMPLETED`; consumes cancellation | No leave/expense/attendance truth. Terminal results must be deterministic and duplicate-safe. |
| Notification | Preference/delivery APIs; consumes subscribed events | Delivery operational events | No source business state. Delivery failure cannot block domain commits. |
| Audit | Restricted search API; consumes audit events/calls | None required | No authorization source. Append-only/redacted/tamper-resistant records. |
| Reporting (deferred) | Report APIs; consumes approved events | None required | No transactional write-back. Projection lag must be visible. |

Announcements remain a Notification capability initially. Internal support tickets are deferred because the supplied requirements do not establish an owner, lifecycle, or whether GDB has an existing ticket system.
