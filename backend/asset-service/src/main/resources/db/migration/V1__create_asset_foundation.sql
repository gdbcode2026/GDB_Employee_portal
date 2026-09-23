-- type is freeform text: no asset classification vocabulary is documented anywhere in this
-- repository, and none is invented here.
CREATE TABLE assets (
  id UUID PRIMARY KEY,
  tag VARCHAR(64) NOT NULL,
  type VARCHAR(120) NOT NULL,
  serial VARCHAR(120),
  status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_assets_tag UNIQUE (tag)
);

-- No endpoint transitions an asset to RETIRED: no retirement policy is documented anywhere
-- (docs/ARCHITECTURE_REVIEW.md leaves depreciation/retention undecided), so the state exists
-- in the enum only, matching DATABASE.md, with no reachable trigger in this increment.
CREATE TABLE asset_assignments (
  id UUID PRIMARY KEY,
  asset_id UUID NOT NULL REFERENCES assets(id),
  employee_ref UUID NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL,
  returned_at TIMESTAMPTZ,
  condition_notes VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_asset_assignments_asset_id ON asset_assignments (asset_id);
CREATE INDEX ix_asset_assignments_employee_ref ON asset_assignments (employee_ref);

-- Only one open (not-yet-returned) assignment per asset at a time.
CREATE UNIQUE INDEX ux_asset_assignments_open_asset ON asset_assignments (asset_id) WHERE returned_at IS NULL;

-- workflow_ref is populated once an administrator starts an approval instance for this
-- request directly against Workflow Service (see AssetRequestService) - Asset Service does
-- not start the workflow itself; see the class-level Javadoc for the full rationale.
CREATE TABLE asset_requests (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  type VARCHAR(120) NOT NULL,
  justification VARCHAR(2000),
  status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
  workflow_ref UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_asset_requests_employee_ref ON asset_requests (employee_ref);

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

-- Inbox for EMPLOYEE_DEACTIVATED and WORKFLOW_COMPLETED, the two events Asset consumes per
-- docs/architecture/COMMUNICATION.md's event contract table.
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);
