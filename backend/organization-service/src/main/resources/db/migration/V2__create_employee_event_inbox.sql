-- Idempotency inbox for consumed domain events (same convention as audit-service's V1).
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
