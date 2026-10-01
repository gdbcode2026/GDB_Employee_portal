-- Employee Compensation Management + Statutory Profile + Payroll Exceptions extension. No
-- statutory rate, tax slab, threshold, eligibility rule, GDB-specific salary policy, or real
-- identifier value is introduced anywhere in this migration.

-- Administrative on/off switch for a compensation record, independent of effective-dating.
ALTER TABLE employee_compensations ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

-- Pay component catalogue gains an active flag and the platform's standard audit columns (it had
-- none before - it was seeded, read-only schema until this task added management APIs).
ALTER TABLE pay_components ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE pay_components ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE pay_components ADD COLUMN created_by VARCHAR(128);
ALTER TABLE pay_components ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE pay_components ADD COLUMN updated_by VARCHAR(128);
ALTER TABLE pay_components ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- code already has a unique constraint from V1 (uq_pay_components_code) - no change needed here.

-- One employee-level statutory applicability record (PF/EPF, ESI, Professional Tax). No rate,
-- threshold, or eligibility rule is stored - only applicability status, identifiers, and the
-- dates each scheme's applicability began/ended. Identifiers are sensitive - never logged.
CREATE TABLE employee_statutory_profiles (
  id UUID PRIMARY KEY,
  employee_ref UUID NOT NULL,
  pf_status VARCHAR(24) NOT NULL,
  pf_uan VARCHAR(32),
  pf_member_id VARCHAR(32),
  pf_effective_from DATE,
  pf_effective_to DATE,
  esi_status VARCHAR(24) NOT NULL,
  esi_identifier VARCHAR(32),
  esi_effective_from DATE,
  esi_effective_to DATE,
  pt_status VARCHAR(24) NOT NULL,
  pt_jurisdiction VARCHAR(64),
  pt_effective_from DATE,
  pt_effective_to DATE,
  created_at TIMESTAMPTZ NOT NULL,
  created_by VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL,
  updated_by VARCHAR(128),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_employee_statutory_profiles_employee UNIQUE (employee_ref)
);

-- payroll_exceptions: relax uniqueness from (run_id, employee_ref) to (run_id, employee_ref,
-- reason) - an employee may now have more than one distinct configuration/data-quality exception
-- in the same run (e.g. missing PF identifier and missing ESI identifier at once), never a
-- duplicate of the same reason. Add a resolution-tracking status.
ALTER TABLE payroll_exceptions DROP CONSTRAINT uq_payroll_exceptions_run_employee;
ALTER TABLE payroll_exceptions ADD CONSTRAINT uq_payroll_exceptions_run_employee_reason UNIQUE (run_id, employee_ref, reason);
ALTER TABLE payroll_exceptions ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN';
ALTER TABLE payroll_exceptions ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE payroll_exceptions ADD COLUMN resolved_by VARCHAR(128);
