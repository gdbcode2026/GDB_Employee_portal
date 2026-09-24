-- total is server-computed as the sum of the claim's expense lines, never client-supplied,
-- to avoid inventing a claim/line-total reconciliation rule nothing documents.
CREATE TABLE expense_claims (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  currency VARCHAR(8) NOT NULL,
  total NUMERIC(14,2) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  workflow_ref UUID,
  decided_by VARCHAR(128),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_expense_claims_employee_ref ON expense_claims (employee_ref);

-- Lines are replaced wholesale on each PATCH while the claim is DRAFT - no separate
-- line-management endpoint is documented in API.md.
CREATE TABLE expense_lines (
  id UUID PRIMARY KEY,
  claim_id UUID NOT NULL REFERENCES expense_claims(id),
  line_date DATE NOT NULL,
  category VARCHAR(120) NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  description VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_expense_lines_claim_id ON expense_lines (claim_id);

-- document_ref is an opaque cross-service reference to a Document Service record - never an
-- enforced database FK, and never synchronously validated against Document Service, per the
-- documented *_ref convention (docs/database/DATABASE.md).
CREATE TABLE receipt_references (
  id UUID PRIMARY KEY,
  claim_id UUID NOT NULL REFERENCES expense_claims(id),
  document_ref UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_receipt_references_claim_id ON receipt_references (claim_id);

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

-- Inbox for WORKFLOW_COMPLETED, the one event docs/architecture/COMMUNICATION.md documents
-- Expense as consuming.
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);
