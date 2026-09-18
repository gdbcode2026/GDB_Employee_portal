-- Minimal reference data: static leave type definitions. No allocation policy is implied -
-- allocated/used/reserved amounts live only in leave_balances, per employee, and are never
-- seeded here (see LeaveBalance in the application code for why that is a flagged gap).
CREATE TABLE leave_types (
  id UUID PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT uq_leave_types_code UNIQUE (code)
);

INSERT INTO leave_types (id, code, name, active) VALUES
  (gen_random_uuid(), 'ANNUAL', 'Annual Leave', TRUE),
  (gen_random_uuid(), 'SICK', 'Sick Leave', TRUE),
  (gen_random_uuid(), 'CASUAL', 'Casual Leave', TRUE);

CREATE TABLE leave_balances (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  leave_type_id UUID NOT NULL REFERENCES leave_types(id),
  period_year INTEGER NOT NULL,
  allocated NUMERIC(12,2) NOT NULL,
  used NUMERIC(12,2) NOT NULL DEFAULT 0,
  reserved NUMERIC(12,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_leave_balances_employee_type_year UNIQUE (employee_ref, leave_type_id, period_year)
);

CREATE INDEX ix_leave_balances_employee_ref ON leave_balances (employee_ref);

CREATE TABLE leave_requests (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  leave_type_id UUID NOT NULL REFERENCES leave_types(id),
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  units NUMERIC(12,2) NOT NULL,
  reason VARCHAR(500),
  status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
  decided_by VARCHAR(128),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_leave_requests_employee_ref ON leave_requests (employee_ref);

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
