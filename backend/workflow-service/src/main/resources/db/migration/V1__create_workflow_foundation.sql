-- request_type is exactly WORKFLOWS.md's documented list of six supported types.
-- rules_json is stored opaque and never interpreted by this service: approval-stage
-- structure, approver resolution, SLA durations, delegation eligibility, and escalation
-- targets are explicitly "configuration inputs for GDB - not assumed company policy"
-- (docs/workflows/WORKFLOWS.md), so nothing here parses or enforces its contents.
CREATE TABLE workflow_definitions (
  id UUID PRIMARY KEY,
  request_type VARCHAR(32) NOT NULL,
  definition_version INTEGER NOT NULL,
  rules_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_workflow_definitions_type_version UNIQUE (request_type, definition_version)
);

-- subject_ref is a cross-service reference to the owning domain's own request record
-- (e.g. a LeaveRequest id) - never an enforced database FK, per DATABASE.md's *_ref convention.
CREATE TABLE workflow_instances (
  id UUID PRIMARY KEY,
  definition_id UUID NOT NULL REFERENCES workflow_definitions(id),
  subject_type VARCHAR(32) NOT NULL,
  subject_ref UUID NOT NULL,
  requester_ref UUID NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  due_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_workflow_instances_requester_ref ON workflow_instances (requester_ref);
CREATE INDEX ix_workflow_instances_subject_ref ON workflow_instances (subject_ref);

CREATE TABLE approval_tasks (
  id UUID PRIMARY KEY,
  instance_id UUID NOT NULL REFERENCES workflow_instances(id),
  sequence_number INTEGER NOT NULL,
  assignee_ref UUID NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  decision VARCHAR(16),
  comment VARCHAR(2000),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_approval_tasks_instance_sequence UNIQUE (instance_id, sequence_number)
);

CREATE INDEX ix_approval_tasks_instance_id ON approval_tasks (instance_id);
CREATE INDEX ix_approval_tasks_assignee_ref ON approval_tasks (assignee_ref);

-- One active delegation per task at a time; only the original assignee may create one
-- (no re-delegation chains) - see WorkflowAccessGuard for the full rationale.
CREATE TABLE delegations (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES approval_tasks(id),
  delegator_ref UUID NOT NULL,
  delegate_ref UUID NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_delegations_task_id ON delegations (task_id);

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
