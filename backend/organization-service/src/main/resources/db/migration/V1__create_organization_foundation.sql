CREATE TABLE departments (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_departments_code UNIQUE (code)
);

CREATE TABLE teams (
  id UUID PRIMARY KEY,
  department_id UUID NOT NULL REFERENCES departments(id),
  name VARCHAR(160) NOT NULL,
  code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_teams_department_code UNIQUE (department_id, code)
);

CREATE INDEX ix_teams_department_id ON teams (department_id);

CREATE TABLE reporting_relations (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  manager_employee_ref UUID NOT NULL,
  effective_start_date DATE NOT NULL,
  effective_end_date DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_reporting_relations_not_self CHECK (employee_ref <> manager_employee_ref)
);

-- An employee may have at most one ACTIVE reporting relation at a time.
CREATE UNIQUE INDEX ux_reporting_relations_active_employee ON reporting_relations (employee_ref) WHERE status = 'ACTIVE';

-- Supports the recursive reporting-scope resolution query.
CREATE INDEX ix_reporting_relations_manager_active ON reporting_relations (manager_employee_ref) WHERE status = 'ACTIVE';
