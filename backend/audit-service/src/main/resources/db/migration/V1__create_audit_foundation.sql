CREATE TABLE audit_entries (
  id UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL,
  actor_ref VARCHAR(128), action VARCHAR(128) NOT NULL,
  resource_type VARCHAR(128) NOT NULL, resource_ref VARCHAR(128) NOT NULL,
  outcome VARCHAR(32) NOT NULL, correlation_id UUID NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb, integrity_hash VARCHAR(128)
);
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY, event_type VARCHAR(160) NOT NULL, payload JSONB NOT NULL,
  correlation_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ
);
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
