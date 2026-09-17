-- Required by the domain-event envelope (see docs/architecture/COMMUNICATION.md); the
-- table is not yet in production use, so this is a plain additive column, no backfill.
ALTER TABLE outbox_events ADD COLUMN aggregate_id UUID NOT NULL;

CREATE INDEX ix_outbox_events_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;
