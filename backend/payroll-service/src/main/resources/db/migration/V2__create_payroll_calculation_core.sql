-- Phase 2 (Calculation Core). No statutory rate, tax slab, threshold, eligibility rule, or
-- GDB-specific salary/compensation amount is introduced anywhere in this migration.

-- Configuration-driven strategy selection (Section H's technical model, extended): which
-- pluggable calculation strategy applies to a component is data, never a hard-coded branch.
-- NULL means "use the default FixedAmountStrategy" - no formula, no rate.
ALTER TABLE compensation_components ADD COLUMN calculation_strategy_code VARCHAR(64);

-- One calculated result per employee per run (Section E/F). Immutable once written; a reprocess
-- of a not-yet-FINALIZED run deletes and recreates rows rather than mutating them, so a run can
-- never accumulate duplicate lines for the same employee (item 11's idempotency requirement).
CREATE TABLE payroll_run_lines (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES payroll_runs(id),
  employee_ref UUID NOT NULL,
  gross_pay NUMERIC(14,2) NOT NULL,
  total_deductions NUMERIC(14,2) NOT NULL,
  total_employer_contributions NUMERIC(14,2) NOT NULL,
  net_pay NUMERIC(14,2) NOT NULL,
  component_breakdown JSONB NOT NULL,
  calculated_at TIMESTAMPTZ NOT NULL,
  created_by VARCHAR(128),
  CONSTRAINT uq_payroll_run_lines_run_employee UNIQUE (run_id, employee_ref)
);

CREATE INDEX ix_payroll_run_lines_run_id ON payroll_run_lines (run_id);

-- Recorded instead of a line for an employee with no effective compensation (item 10). The
-- business decision of whether this should block the run is left unresolved (Section I/X); this
-- table only makes the exception visible and auditable, never automatically skipped or blocking.
CREATE TABLE payroll_exceptions (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES payroll_runs(id),
  employee_ref UUID NOT NULL,
  reason VARCHAR(40) NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_payroll_exceptions_run_employee UNIQUE (run_id, employee_ref)
);

CREATE INDEX ix_payroll_exceptions_run_id ON payroll_exceptions (run_id);

-- Payroll's own snapshot of attendance.finalized.v1 events (Section I). Only the fields the
-- event actually carries are stored; no attendance status other than "finalized" is possible
-- since that is the only case the event is ever published for.
CREATE TABLE payroll_attendance_inputs (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  work_date DATE NOT NULL,
  attendance_ref UUID NOT NULL,
  source_event_id UUID NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_payroll_attendance_inputs_employee_date UNIQUE (employee_ref, work_date)
);

CREATE INDEX ix_payroll_attendance_inputs_employee_date ON payroll_attendance_inputs (employee_ref, work_date);

-- Payroll's own snapshot of leave.approved.v1 events (Section I).
CREATE TABLE payroll_leave_inputs (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  leave_request_ref UUID NOT NULL,
  approved_units NUMERIC(12,2) NOT NULL,
  source_event_id UUID NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_payroll_leave_inputs_request UNIQUE (leave_request_ref)
);

CREATE INDEX ix_payroll_leave_inputs_employee_ref ON payroll_leave_inputs (employee_ref);

-- "Common India Payroll V1 Baseline" generic product component types (PAYROLL_REQUIREMENTS.md).
-- Names/types only - no rate, amount, threshold, or eligibility rule. This is a provisional,
-- configurable product default, NOT GDB's approved statutory catalogue (still PENDING_GDB_APPROVAL).
INSERT INTO pay_components (id, code, name, type) VALUES
  (gen_random_uuid(), 'BASIC_SALARY',    'Basic Salary',     'EARNING'),
  (gen_random_uuid(), 'HRA',             'HRA',              'EARNING'),
  (gen_random_uuid(), 'OTHER_ALLOWANCE', 'Other Allowance',  'EARNING'),
  (gen_random_uuid(), 'BONUS',           'Bonus',            'EARNING'),
  (gen_random_uuid(), 'OVERTIME',        'Overtime',         'EARNING'),
  (gen_random_uuid(), 'OTHER_EARNING',   'Other Earning',    'EARNING'),
  (gen_random_uuid(), 'PF',              'Provident Fund',   'DEDUCTION'),
  (gen_random_uuid(), 'ESI',             'Employee State Insurance', 'DEDUCTION'),
  (gen_random_uuid(), 'PROFESSIONAL_TAX','Professional Tax', 'DEDUCTION'),
  (gen_random_uuid(), 'TDS',             'Tax Deducted at Source', 'DEDUCTION'),
  (gen_random_uuid(), 'LOAN_ADVANCE',    'Loan/Advance',     'DEDUCTION'),
  (gen_random_uuid(), 'OTHER_DEDUCTION', 'Other Deduction',  'DEDUCTION'),
  (gen_random_uuid(), 'EMPLOYER_PF',     'Employer PF Contribution',  'EMPLOYER_CONTRIBUTION'),
  (gen_random_uuid(), 'EMPLOYER_ESI',    'Employer ESI Contribution', 'EMPLOYER_CONTRIBUTION');
