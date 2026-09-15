CREATE TABLE notification_preferences (id UUID PRIMARY KEY, employee_ref UUID NOT NULL, channel VARCHAR(32) NOT NULL, enabled BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(employee_ref, channel));
CREATE TABLE notification_deliveries (id UUID PRIMARY KEY, event_id UUID NOT NULL, recipient_ref UUID NOT NULL, status VARCHAR(32) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE outbox_events (id UUID PRIMARY KEY, event_type VARCHAR(160) NOT NULL, payload JSONB NOT NULL, correlation_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ);
CREATE TABLE processed_events (event_id UUID PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
