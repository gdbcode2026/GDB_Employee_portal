-- Phase 1 foundation only (PAYROLL_REQUIREMENTS.md Section F). No pay-component catalogue row,
-- no compensation amount, and no calculation table (payroll_run_lines, payslips) is created here -
-- those depend on business decisions and a calculation pipeline that do not exist yet (Section Y).

CREATE TABLE payroll_periods (
  id UUID PRIMARY KEY,
  year INTEGER NOT NULL,
  month INTEGER NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  cut_off_date DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_payroll_periods_year_month UNIQUE (year, month)
);

CREATE TABLE payroll_runs (
  id UUID PRIMARY KEY,
  period_id UUID NOT NULL REFERENCES payroll_periods(id),
  run_type VARCHAR(16) NOT NULL DEFAULT 'REGULAR',
  corrects_run_id UUID REFERENCES payroll_runs(id),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  initiated_by VARCHAR(128),
  approved_by VARCHAR(128),
  approved_at TIMESTAMPTZ,
  finalized_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

-- Section T's idempotency key (period_id, run_type, corrects_run_id). This Phase 1 foundation
-- only ever creates REGULAR runs with a null corrects_run_id, so a partial unique index on that
-- exact case is what enforces "one regular run per period" at the database level; a future
-- ADJUSTMENT-run phase will need its own uniqueness handling for non-null corrects_run_id.
CREATE UNIQUE INDEX uq_payroll_runs_period_regular ON payroll_runs (period_id)
  WHERE run_type = 'REGULAR' AND corrects_run_id IS NULL;

CREATE INDEX ix_payroll_runs_period_id ON payroll_runs (period_id);

-- Employee snapshot captured once at DRAFT creation (Section I) - never re-queried.
CREATE TABLE payroll_run_employees (
  run_id UUID NOT NULL REFERENCES payroll_runs(id),
  employee_ref UUID NOT NULL,
  PRIMARY KEY (run_id, employee_ref)
);

-- Effective-dated compensation (decision 4). No amount, currency other than INR, or catalogue
-- content is seeded here - PENDING_GDB_APPROVAL per Section X. No REST API exposes these tables
-- in Phase 1 (Section O documents none); they exist for Phase 2's calculation pipeline to read.
CREATE TABLE employee_compensations (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  currency VARCHAR(3) NOT NULL,
  pay_frequency VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
  effective_from DATE NOT NULL,
  effective_to DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_employee_compensations_employee_ref ON employee_compensations (employee_ref);

CREATE TABLE compensation_components (
  id UUID PRIMARY KEY,
  compensation_id UUID NOT NULL REFERENCES employee_compensations(id),
  component_code VARCHAR(64) NOT NULL,
  component_type VARCHAR(24) NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  proration_policy_code VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128)
);

CREATE INDEX ix_compensation_components_compensation_id ON compensation_components (compensation_id);

-- Catalogue master (Section F). Deliberately empty: the actual pay-component catalogue is
-- PENDING_GDB_APPROVAL (Section X) and this task explicitly forbids seeding or inventing it.
CREATE TABLE pay_components (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(24) NOT NULL,
  CONSTRAINT uq_pay_components_code UNIQUE (code)
);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  event_type VARCHAR(160) NOT NULL,
  aggregate_id UUID NOT NULL,
  payload JSONB NOT NULL,
  correlation_id UUID NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);

CREATE INDEX ix_outbox_events_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);
