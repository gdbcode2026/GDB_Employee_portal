# Communication

## Synchronous REST

REST calls are versioned (`/api/v1`), time-bounded, authenticated service-to-service, and reserved for an immediate answer. Typical calls are Gateway to the target service, Employee lookup while creating a domain record, Organization lookup for current team scope, and Document authorization before download. Avoid long chains; a request should normally cross no more than one downstream domain boundary.

The gateway must not contain business orchestration. Services retain responsibility for authorization and validation even when the gateway has authenticated the caller.

## Asynchronous events

RabbitMQ distributes committed facts, including `EMPLOYEE_CREATED`, `EMPLOYEE_UPDATED`, `LEAVE_APPLIED`, `LEAVE_APPROVED`, `LEAVE_REJECTED`, `ATTENDANCE_FINALIZED`, `EXPENSE_SUBMITTED`, `EXPENSE_APPROVED`, `PAYROLL_PROCESSED`, `DOCUMENT_UPLOADED`, and `TICKET_CREATED`.

Publishers write an outbox entry in the same database transaction as their state change. A relay publishes to RabbitMQ and marks it dispatched. Consumers are idempotent, track processed event IDs, retry transient failures, and route exhausted messages to a dead-letter queue. Events contain IDs and minimal non-sensitive business context, never credentials or payroll values.

## Event routing

Use a durable topic exchange, for example `gdb.domain.events`, with versioned routing keys such as `leave.applied.v1`. Each consumer has its own durable queue. Schemas are additive within a version; incompatible changes require a new event version and documented migration period.

## Event contract

Every event has envelope fields `eventId UUID`, `eventType`, `eventVersion integer`, `occurredAt TIMESTAMPTZ`, `correlationId UUID`, `producer`, `aggregateId UUID`, and `payload`. Routing keys use `domain.action.v1`. Payloads contain only minimal references/context—never tokens or payroll values. Publishers write outbox rows in the same transaction as the domain mutation. Consumers atomically record `eventId` in an inbox with resulting state, making duplicates harmless; retry transient failure with bounded exponential backoff and route exhausted/unprocessable messages to a per-queue DLQ with reason/attempt headers and alerts.

| Event v1 | Producer | Consumers | Minimum payload |
|---|---|---|---|
| `EMPLOYEE_CREATED` | Employee | Identity*, Organization, Workflow, Notification, Audit, Reporting* | employee/employment ID, status |
| `EMPLOYEE_UPDATED` | Employee | Organization, Notification, Audit, Reporting* | employee ID, non-sensitive changed-field names |
| `EMPLOYEE_DEACTIVATED` | Employee | Identity*, Asset, Document, Workflow, Audit, Reporting* | employee ID, effective time |
| `LEAVE_REQUESTED` | Leave | Workflow, Notification, Audit, Reporting* | request/employee/type IDs, start/end dates |
| `LEAVE_APPROVED` | Leave | Attendance, Notification, Audit, Reporting*, Payroll* | request/employee ID, approved units |
| `LEAVE_REJECTED` | Leave | Notification, Audit, Reporting* | request/employee ID, reason code O |
| `ATTENDANCE_FINALIZED` | Attendance | Notification, Audit, Reporting*, Payroll* | attendance/employee ID, work date/status |
| `ATTENDANCE_REGULARIZATION_APPROVED` | Attendance | Notification, Audit, Reporting* | request/attendance/employee IDs |
| `EXPENSE_SUBMITTED` | Expense | Workflow, Notification, Audit, Reporting* | claim/employee ID, currency, total |
| `EXPENSE_APPROVED` | Expense | Notification, Audit, Reporting* | claim/employee ID, approved total/currency |
| `PAYROLL_PROCESSED` | Payroll* | Notification, Audit, Reporting* | payroll run/period ID, employee count |
| `DOCUMENT_UPLOADED` | Document | Notification, Audit | document ID, owner ID O, classification, scan status |
| `ASSET_ASSIGNED` | Asset | Notification, Audit, Reporting* | asset/assignment/employee IDs |
| `WORKFLOW_COMPLETED` | Workflow | Leave, Attendance, Expense, Asset, Document, Notification, Audit | workflow ID, subject type/ID, outcome, decision time |

`*` denotes a deferred consumer/service or a consumer only activated when its integration is approved.
