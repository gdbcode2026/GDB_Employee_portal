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

Deliver Payroll/payslips only after finance requirements are approved. Build Reporting projections and controlled exports.

## Phase 6 — Production readiness

Load/security testing, backup/restore drills, monitoring/alerting, penetration testing, data migration, runbooks, rollout and rollback validation.
