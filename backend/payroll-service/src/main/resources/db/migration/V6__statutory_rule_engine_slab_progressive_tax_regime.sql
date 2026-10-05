-- Extends the Configurable Statutory + Tax Rule Engine (V5) with SLAB_BASED/PROGRESSIVE_TAX
-- calculation shapes and tax-regime-aware resolution, per the India Payroll V1 research findings
-- (docs/INDIA_PAYROLL_V1_RULE_SOURCES.md Section 5: Professional Tax's multi-bracket slab
-- structure and salary TDS's progressive calculation cannot be expressed by the three previously
-- existing calculation types). No PF/ESI/Professional Tax/TDS rate, slab, threshold, or
-- eligibility VALUE is introduced by this migration - only generic structure. Ordered brackets
-- themselves live inside the existing `parameters` JSONB column (StatutoryRuleParameters.brackets)
-- - no new table is needed for them.

-- tax_regime is the TDS-side analogue of jurisdiction (e.g. Professional Tax's state/local-body
-- dimension): a free-form, employee-elected regime identifier, never a value this codebase
-- decides or defaults. NULL means "regime-independent" (PF/ESI, and any non-regime-aware TDS
-- configuration).
ALTER TABLE statutory_rules ADD COLUMN tax_regime VARCHAR(32);

-- Version numbering is now scoped per (code, jurisdiction, tax_regime) - see
-- StatutoryRuleService's create()/activate() for the write-time enforcement this constraint
-- backs. As with the superseded (code, jurisdiction, rule_version) constraint, Postgres treats
-- two NULL tax_regime values as distinct for uniqueness purposes, so (as already documented for
-- jurisdiction) this constraint alone does not catch a concurrency race between two simultaneous
-- "create next version" calls for the same non-jurisdictional, regime-independent code -
-- StatutoryRuleService's own read-then-increment logic remains the only guard for that case.
ALTER TABLE statutory_rules DROP CONSTRAINT uq_statutory_rules_code_jurisdiction_version;
ALTER TABLE statutory_rules ADD CONSTRAINT uq_statutory_rules_code_jurisdiction_tax_regime_version
  UNIQUE (code, jurisdiction, tax_regime, rule_version);

DROP INDEX IF EXISTS ix_statutory_rules_code_jurisdiction_status;
CREATE INDEX ix_statutory_rules_code_jurisdiction_tax_regime_status
  ON statutory_rules (code, jurisdiction, tax_regime, status);

-- Employee-elected tax regime (India Payroll V1 architecture-extension task), the employee-level
-- counterpart that makes tax-regime-aware resolution possible: EmployeeStatutoryProfile already
-- carries pt_jurisdiction for Professional Tax resolution (Compensation Management task) - this
-- adds the equivalent field for TDS. No regime value/name is seeded or assumed; this codebase
-- does not decide which tax regime GDB uses or offers.
ALTER TABLE employee_statutory_profiles ADD COLUMN tax_regime VARCHAR(32);
