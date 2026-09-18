CREATE TABLE review_cycles (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

-- No cycle reference: DATABASE.md lists Goal as employee ref/title/description/target/status
-- only, with no relationship to ReviewCycle - goals are standing per-employee objectives,
-- not tied to a review period.
CREATE TABLE goals (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  title VARCHAR(200) NOT NULL,
  description VARCHAR(2000),
  target VARCHAR(500),
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_goals_employee_ref ON goals (employee_ref);

-- rating is freeform text: no rating scale/scoring formula is documented anywhere in the
-- repository, and none is invented here per explicit instruction.
CREATE TABLE performance_reviews (
  id UUID PRIMARY KEY,
  cycle_id UUID NOT NULL REFERENCES review_cycles(id),
  employee_ref UUID NOT NULL,
  reviewer_ref UUID NOT NULL,
  rating VARCHAR(100),
  comments VARCHAR(4000),
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  submitted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_performance_reviews_cycle_id ON performance_reviews (cycle_id);
CREATE INDEX ix_performance_reviews_employee_ref ON performance_reviews (employee_ref);
CREATE INDEX ix_performance_reviews_reviewer_ref ON performance_reviews (reviewer_ref);

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
