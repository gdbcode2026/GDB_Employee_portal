-- Payslip generation and employee payslip access. No statutory rate, tax slab, threshold,
-- eligibility rule, or GDB-specific salary/compensation amount is introduced anywhere here -
-- payslip content is always read from the already-calculated payroll_run_lines row.

-- One generated payslip per employee per run (item 1/13). documentRef points at the Document
-- Service record holding the PDF - no binary is ever stored here. Immutable once created; an
-- adjustment run gets its own row via its own run_id, never a mutation of this one (item 14).
CREATE TABLE payslips (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  run_id UUID NOT NULL REFERENCES payroll_runs(id),
  period_id UUID NOT NULL REFERENCES payroll_periods(id),
  document_ref UUID NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_payslips_run_employee UNIQUE (run_id, employee_ref)
);

CREATE INDEX ix_payslips_employee_ref ON payslips (employee_ref);
CREATE INDEX ix_payslips_employee_period ON payslips (employee_ref, period_id);

-- Recorded when payslip generation fails for one employee within a finalized run (item 7), so a
-- failure is never silently swallowed. Upserted per (run_id, employee_ref) on retry - never
-- accumulated - mirroring payroll_exceptions' precedent for a technical/process concern.
CREATE TABLE payslip_generation_failures (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES payroll_runs(id),
  employee_ref UUID NOT NULL,
  failure_type VARCHAR(160),
  failure_message VARCHAR(2000),
  occurred_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_payslip_generation_failures_run_employee UNIQUE (run_id, employee_ref)
);

CREATE INDEX ix_payslip_generation_failures_run_id ON payslip_generation_failures (run_id);
