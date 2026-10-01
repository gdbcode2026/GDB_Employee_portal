# Database Strategy

## Ownership

Every domain service receives a separate PostgreSQL database (or, as an operational interim only, a separately credentialed database/schema with no cross-service grants). The preferred production model is one database per service. No service reads another service's tables, runs joins across domains, or writes a shared schema.

| Database owner | Primary data |
|---|---|
| auth | identity, roles, sessions/client metadata |
| employee | employee and employment profiles |
| organization | structure and reporting graph |
| attendance | time, WFH, regularization, holiday data |
| leave | leave types, balances, requests |
| payroll | pay components, runs, payslip metadata |
| expense | claims, receipts metadata, reimbursement state |
| project | projects, memberships, tasks |
| performance | goals and reviews |
| document | document/policy metadata and access data |
| asset | asset inventory and assignment |
| workflow | definitions and approval tasks |
| notification | preferences/templates/delivery state |
| audit | append-only audit entries |
| reporting | event-fed read projections |

## Data practices

Use Flyway migrations owned and executed by each service. Database users are least-privilege and distinct per service. Encrypt production storage at rest and connections in transit. Backups, recovery objectives, retention, PII classification, and deletion/legal-hold policies require business/security decisions before go-live. Redis stores only cache, rate-limit, or ephemeral state—not authoritative employee or payroll data.

Document binaries are stored outside PostgreSQL in private object storage; Document stores object keys, integrity metadata, classification, and authorization records.

## Detailed domain model

All primary keys are `UUID`; timestamps are `TIMESTAMPTZ`; mutable entities have `created_at`, `created_by`, `updated_at`, `updated_by`, and optimistic `version` unless stated. `R` is required, `O` optional, `S` sensitive. `*_id` is a local foreign key; `*_ref` is a cross-service UUID reference and never an enforced database FK.

| Owner | Entity and lifecycle | Fields and relationships | Audit / sensitivity |
|---|---|---|---|
| Auth | Identity `ACTIVE/LOCKED/DISABLED`; Role, Permission, RoleGrant, Client, SessionDevice | Identity: `id`, `subject varchar R`, `email varchar S R`, `display_name O`, `status`; Grant: `identity_id FK`, `role_id FK`, `scope_type`, `scope_ref O`; SessionDevice: `identity_id FK`, `device_hash S`, `expires_at` | Credentials are not stored unless local auth is approved. Grant/session changes audited. |
| Employee | Employee `ACTIVE/INACTIVE`; Employment; EmergencyContact | Employee: `employee_number varchar S R`, names/email/phone `S`, `identity_subject O`; Employment: `employee_id FK`, start/end dates, job title, type, status; Contact: `employee_id FK`, name/phone/relationship `S` | HR writes and PII reads audited. |
| Organization | Department/Team/Position/Location `ACTIVE/INACTIVE`; ReportingRelation | Name/code R; Team has `department_id FK`; Location address `S O`; relation has `employee_ref R`, `manager_employee_ref R`, effective dates/status | Enforce no hierarchy/reporting cycles; audit structural change. |
| Attendance | Shift; Holiday; AttendanceRecord `DRAFT/FINALIZED`; WfhRequest; RegularizationRequest | Record: `employee_ref`, `work_date date`, check-in/out O, status; request: employee ref, reason `S`, dates, status, `workflow_ref O` | Finalization and decisions audited. |
| Leave | LeaveType; LeaveBalance; LeaveRequest `DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED` | Balance: employee ref, `leave_type_id FK`, period, allocated/used/reserved `decimal(12,2)`; request has type FK, dates, units, reason `S O`, workflow ref | Balance changes and decisions audited. |
| Payroll (foundation, calculation core, approval/finalization, payslip generation, adjustment runs, and compensation/statutory-profile/exception management implemented; real values deferred) | PayComponent `active`; PayrollPeriod; PayrollRun; EmployeeCompensation `ACTIVE/INACTIVE`; CompensationComponent; EmployeeStatutoryProfile; PayrollRunLine; PayrollException `OPEN/RESOLVED`; PayrollAttendanceInput; PayrollLeaveInput; Payslip; PayslipGenerationFailure | Component code/type/amount/active flag `S`; run has `period_id FK`, `run_type` (`REGULAR`/`ADJUSTMENT`), `corrects_run_id` (self-referencing FK, `ADJUSTMENT` only); compensation has employee ref, currency, effective-from/to, status - overlap with another `ACTIVE` record for the same employee is rejected at write time, and a record already read by a `FINALIZED` run's line can no longer be updated; statutory profile has employee ref (unique), PF/ESI/PT status `S` each with identifier `S` and effective dates - a missing identifier is never inferred as not-applicable; run line has gross/deductions/employer-contributions/net pay `S` plus JSON breakdown (no positivity constraint - an adjustment run's line may be negative); exception has run/employee ref/reason (eight reason codes, including missing-compensation/missing-statutory-profile/missing-PF-or-ESI-identifier/invalid-effective-dates/overlapping-compensation/invalid-pay-component/other-configuration-error)/resolution status/resolved-by/resolved-at, unique per `(run_id, employee_ref, reason)`; attendance/leave input snapshots have employee ref and source event ref; payslip references run and employee, storage ref `S`, unique per `(run_id, employee_ref)` - an adjustment run's payslip is an independent row; generation-failure row mirrors the exception pattern for a technical retry concern. All of the above schema now exists and is implemented per `docs/PAYROLL_REQUIREMENTS.md`; `PayComponent` is seeded with generic Common-India-baseline component *names* only (no rate/amount) — no real salary amount, rate, catalogue content, statutory rate/threshold, or HR-vs-Finance authority split has been seeded or decided anywhere. | All values/access/exports highly sensitive and audited; no self-service read or write path exists for compensation, pay-component, or statutory-profile data. |
| Expense | ExpenseClaim `DRAFT/SUBMITTED/APPROVED/REJECTED/REIMBURSED/CANCELLED`; ExpenseLine; ReceiptReference | Claim employee ref/currency/total `decimal(14,2)`/workflow ref; line has claim FK/date/category/amount/description `S`; receipt has claim FK/document ref | Monetary changes audited. |
| Project | Project; ProjectMembership; Task | Project code/name/status/owner ref; membership `project_id FK`, employee ref/role/status; task project FK/assignee ref/title/description/status/due date | Membership/assignment audited. |
| Performance | Goal; ReviewCycle; PerformanceReview | Goal employee ref/title/description `S`/target/status; cycle dates/status; review cycle FK/employee/reviewer refs/rating/comments `S`/status | Reviews restricted/audited. |
| Document | Document `PENDING_SCAN/AVAILABLE/QUARANTINED/ARCHIVED`; Version; AccessGrant; Policy | Document owner ref/classification/status; version document FK/object key/checksum `S`/MIME/size/scan status; grant document FK/subject/permission/expiry; policy document FK/title/status | Download/grant/change audit required. |
| Asset | Asset `AVAILABLE/ASSIGNED/RETIRED`; AssetAssignment; AssetRequest | Asset tag/type/serial `S`/status; assignment asset FK/employee ref/dates/condition notes `S`; request employee/type/justification `S`/workflow ref | Custody changes audited. |
| Workflow | Definition; Instance `RUNNING/APPROVED/REJECTED/CANCELLED/EXPIRED`; ApprovalTask; Delegation | Definition request type/version/`rules_json`; instance definition FK/subject type+ref/requester/status/due; task instance FK/sequence/assignee/status/decision/comment `S`; delegation parties/times/status | Every transition immutable/audited. |
| Notification | Preference; Template; Delivery | Preference employee/channel/enabled; template code/channel/content `S`; delivery recipient/template FK/event/status/provider ref `S`/sent time | Retain only policy-permitted content. |
| Audit | AuditEntry append-only | actor/action/resource/outcome/correlation ID/source IP `S O`/redacted metadata/integrity hash | No application update/delete. |
| Reporting (deferred) | Projection; ReportDefinition; ReportRun | projection source event/data JSON; definition name/query spec; run definition FK/requester/status/output ref | Export restricted/audited. |

## Integration and idempotency

Each domain mutation, audit intent, and outbox row commits in one local transaction—never a distributed transaction. Consumers atomically persist the event ID in an inbox/processed-event table with their state change, acknowledging duplicates. Create/finalize API operations accept an idempotency key. Flyway migrations and least-privilege database credentials are service-owned.
