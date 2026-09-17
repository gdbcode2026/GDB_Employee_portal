CREATE TABLE employees (
  id UUID PRIMARY KEY,
  employee_number VARCHAR(64) NOT NULL,
  first_name VARCHAR(120) NOT NULL,
  last_name VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(32),
  identity_subject VARCHAR(255),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  deactivated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_employees_employee_number UNIQUE (employee_number),
  CONSTRAINT uq_employees_email UNIQUE (email)
);

-- Not every identity is linked to an employee record yet, and not every employee has a
-- linked identity, but where one exists it must resolve to exactly one employee.
CREATE UNIQUE INDEX ux_employees_identity_subject ON employees (identity_subject) WHERE identity_subject IS NOT NULL;

CREATE TABLE employments (
  id UUID PRIMARY KEY,
  employee_id UUID NOT NULL REFERENCES employees(id),
  job_title VARCHAR(160) NOT NULL,
  employment_type VARCHAR(24) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_employments_employee_id ON employments (employee_id);

-- An employee has at most one ACTIVE employment record at a time.
CREATE UNIQUE INDEX ux_employments_active_employee ON employments (employee_id) WHERE status = 'ACTIVE';

CREATE TABLE emergency_contacts (
  id UUID PRIMARY KEY,
  employee_id UUID NOT NULL REFERENCES employees(id),
  name VARCHAR(160) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  relationship VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_emergency_contacts_employee_id ON emergency_contacts (employee_id);

-- Transactional outbox for EMPLOYEE_CREATED/UPDATED/DEACTIVATED. No consumer/relay is wired
-- up yet (matches the rest of the platform foundation); rows are written transactionally
-- alongside the domain mutation and await a future relay.
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  event_type VARCHAR(160) NOT NULL,
  payload JSONB NOT NULL,
  correlation_id UUID NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);
