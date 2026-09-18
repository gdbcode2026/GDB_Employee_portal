-- classification is freeform text: no classification vocabulary is documented anywhere in
-- this repository (docs/ARCHITECTURE_REVIEW.md lists "data classification" as a decision
-- still required from GDB), so none is invented here.
CREATE TABLE documents (
  id UUID PRIMARY KEY,
  owner_ref UUID NOT NULL,
  classification VARCHAR(200),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING_SCAN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_documents_owner_ref ON documents (owner_ref);

-- object_key is a server-generated opaque reference, not tied to any storage provider (none
-- is decided per docs/ARCHITECTURE_REVIEW.md item 3) - no bytes are stored or retrieved here.
CREATE TABLE document_versions (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id),
  version_number INTEGER NOT NULL,
  object_key VARCHAR(200) NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_document_versions_document_number UNIQUE (document_id, version_number)
);

CREATE INDEX ix_document_versions_document_id ON document_versions (document_id);

-- Documented in DATABASE.md (Document owns: ..., AccessGrant, ...) but API.md defines no
-- endpoint to create or manage grants, so this table is schema-only in this increment -
-- no service/controller reads or writes it. See DocumentAccessGuard for the rationale.
CREATE TABLE access_grants (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id),
  subject_ref UUID NOT NULL,
  permission VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_access_grants_document_id ON access_grants (document_id);

CREATE TABLE policies (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id),
  title VARCHAR(200) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0
);

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

-- Inbox for EMPLOYEE_DEACTIVATED, the one event Document consumes per
-- docs/architecture/COMMUNICATION.md's event contract table.
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);
