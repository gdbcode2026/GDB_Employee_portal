-- The real in-app notification record (V1 scope). Distinct from the pre-existing
-- notification_preferences/notification_deliveries tables (V1 foundation migration), which
-- stay unused: user preferences and multi-channel delivery tracking are explicitly out of
-- scope for this increment.
CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  recipient_employee_ref UUID NOT NULL,
  type VARCHAR(48) NOT NULL,
  title VARCHAR(200) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  source_event_id UUID NOT NULL,
  read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

-- Serves both the recipient-scoped list query and its default sort (createdAt desc).
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_employee_ref, created_at DESC);

-- Serves the unread-count query used by both GET /notifications and POST /read-all.
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_employee_ref) WHERE read = FALSE;
