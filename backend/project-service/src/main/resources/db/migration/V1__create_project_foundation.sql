CREATE TABLE projects (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(160) NOT NULL,
  owner_ref UUID NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_projects_code UNIQUE (code)
);

CREATE TABLE project_memberships (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  employee_ref UUID NOT NULL,
  role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  -- One membership row per employee per project; removal toggles status rather than
  -- deleting the row, preserving the audit trail DATABASE.md requires.
  CONSTRAINT uq_project_memberships_project_employee UNIQUE (project_id, employee_ref)
);

CREATE INDEX ix_project_memberships_project_id ON project_memberships (project_id);
CREATE INDEX ix_project_memberships_employee_ref ON project_memberships (employee_ref);

CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  assignee_ref UUID,
  title VARCHAR(200) NOT NULL,
  description VARCHAR(2000),
  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  due_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_tasks_project_id ON tasks (project_id);
CREATE INDEX ix_tasks_assignee_ref ON tasks (assignee_ref);

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
