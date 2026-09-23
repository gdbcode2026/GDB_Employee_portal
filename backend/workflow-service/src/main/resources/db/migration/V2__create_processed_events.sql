-- Inbox for EMPLOYEE_DEACTIVATED, the one event docs/architecture/COMMUNICATION.md documents
-- Workflow as consuming - the idempotency guard for that consumer.
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);
