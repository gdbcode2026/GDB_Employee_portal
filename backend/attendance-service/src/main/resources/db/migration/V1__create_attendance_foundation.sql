CREATE TABLE attendance_records (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  work_date DATE NOT NULL,
  check_in_at TIMESTAMPTZ,
  check_out_at TIMESTAMPTZ,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  -- One record per employee per work date: also the natural idempotency guard for
  -- retried check-in calls (see AttendanceService).
  CONSTRAINT uq_attendance_records_employee_work_date UNIQUE (employee_ref, work_date)
);

CREATE INDEX ix_attendance_records_employee_ref ON attendance_records (employee_ref);

CREATE TABLE wfh_requests (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
  decided_by VARCHAR(128),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_wfh_requests_employee_ref ON wfh_requests (employee_ref);

CREATE TABLE regularization_requests (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  work_date DATE NOT NULL,
  requested_check_in_at TIMESTAMPTZ,
  requested_check_out_at TIMESTAMPTZ,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
  decided_by VARCHAR(128),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_regularization_requests_employee_ref ON regularization_requests (employee_ref);

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

-- Inbox for LEAVE_APPROVED, the one event Attendance consumes per
-- docs/architecture/COMMUNICATION.md's event contract table.
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);
