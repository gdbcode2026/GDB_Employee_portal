# Development Roadmap

## Phase 0 — Discovery and blueprint (complete)

The architecture blueprint, API/event contract baseline, data ownership, RBAC, workflows, security design, coding standards and traceability have been documented. GDB decisions listed in [Architecture Review](ARCHITECTURE_REVIEW.md) remain required before implementation.

## Phase 1 — Platform foundation

Create repository/service templates, API Gateway, Identity/Auth integration, service security baseline, Flyway, PostgreSQL/RabbitMQ/Redis local Compose environment, secret handling, observability baseline, CI checks, and audit event conventions.

## Phase 2 — Employee core

Deliver frontend shell, Employee, Organization, directory, profile, announcements/notifications baseline, RBAC/resource enforcement, and onboarding foundations.

## Phase 3 — Time and requests

Deliver Attendance, Leave, Workflow, holiday/WFH/regularization, approvals, notifications, and audit coverage.

## Phase 4 — Work and employee services

Deliver Project, Performance, Document/policies, Asset, Expense, secure uploads, and support-ticket scope after its owning service is decided.

## Phase 5 — Sensitive finance and reporting

Payroll Foundation, Calculation Core, Approval/Finalization, Payslip Generation, and Adjustment Runs are all implemented at the technical level; real payroll calculation content and every sensitive business function remain gated pending GDB approvals. Phases 1–4 plus adjustment runs (`docs/PAYROLL_REQUIREMENTS.md`) deliver `PayrollPeriod`/`PayrollRun` lifecycle scaffolding (including `PROCESSING`/`CALCULATION_FAILED`), RBAC (`payroll.process`/`payroll.approve`/`payroll.read.all`/`payslip.read.self/all`), audit logging, idempotency, effective-dated compensation resolution, configurable (no-formula) calculation strategies, configurable (no-op) proration, `PayrollRunLine` results, `PayrollException` records for missing compensation, consumption of finalized-attendance/approved-leave events into Payroll's own input snapshots, the maker-checker approve/reject/finalize flow with self-approval prevention, `POST /payroll/runs/{id}/adjustments` (an `ADJUSTMENT` run against a `FINALIZED` original, reusing the identical pipeline), and payslip generation (PDF, Document Service upload, `payslip.generated.v1`, self-only access) - with no real salary/tax/statutory formula, no pay-component catalogue content, and no adjustment/netting accounting policy anywhere. Deliver real payroll calculation values and any payment/provider integration only after finance requirements are approved. Build Reporting projections and controlled exports.

## Phase 6 — Production readiness

Load/security testing, backup/restore drills, monitoring/alerting, penetration testing, data migration, runbooks, rollout and rollback validation.
