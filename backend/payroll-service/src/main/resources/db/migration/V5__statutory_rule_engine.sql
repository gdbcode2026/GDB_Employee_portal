-- Configurable Statutory + Tax Rule Engine. No PF/ESI/Professional Tax/TDS rate, slab,
-- threshold, or eligibility VALUE is seeded anywhere in this migration or this codebase - only
-- the generic, empty structure a future GDB/Finance/Legal-approved configuration will fill
-- through the new management API.

CREATE TABLE statutory_rules (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  rule_type VARCHAR(24) NOT NULL,
  jurisdiction VARCHAR(64),
  rule_version INT NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  calculation_type VARCHAR(24) NOT NULL,
  parameters JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  -- Version numbers are scoped per (code, jurisdiction) - a jurisdiction-aware rule family (e.g.
  -- Professional Tax) has an independent version-1-onward sequence per jurisdiction under the
  -- same code, so two different jurisdictions may each hold their own "version 1" simultaneously.
  -- Overlap between ACTIVE versions of the same code+jurisdiction is prevented at write time by
  -- StatutoryRuleService.activate, not by this constraint (which only guards against a duplicate
  -- version number). NOTE: because jurisdiction is nullable, Postgres treats two NULL
  -- jurisdictions as distinct for uniqueness purposes - this constraint therefore does not catch
  -- a concurrency race between two simultaneous "create next version" calls for the same
  -- non-jurisdictional code; StatutoryRuleService.create's own read-then-increment logic is the
  -- only guard for that narrow case today, a known, documented limitation rather than a silent gap.
  CONSTRAINT uq_statutory_rules_code_jurisdiction_version UNIQUE (code, jurisdiction, rule_version)
);

-- Supports both StatutoryRuleResolver's effective-dated lookup (code, jurisdiction, status) and
-- the management API's per-family version listing (code).
CREATE INDEX ix_statutory_rules_code_jurisdiction_status ON statutory_rules (code, jurisdiction, status);

-- No row is inserted here. Unlike pay_components (seeded with generic Common India Payroll V1
-- Baseline product *names* only, no rate), no statutory rule family is established by this
-- codebase either - every rule family (code), its jurisdiction, and its parameters are created
-- only through POST /api/v1/payroll/statutory-rules once an approved configuration exists. Any
-- test fixture that creates a row here must be explicit, clearly-marked TEST DATA, never treated
-- as real GDB policy.
