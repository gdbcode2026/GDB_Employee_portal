# Payroll V1 — Mandatory Flow Completion Gap Report

**Scope of this review**: the one mandatory flow — Employee Compensation → Attendance + Approved
Leave → Earnings → LOP/Proration → Applicable PF/ESI/PT/TDS → Gross/Deductions/Net Pay →
Approval → Finalization → Payslip → Employee Download — as currently implemented in
`backend/payroll-service`. No code was changed to produce this report. Nothing here invents a
GDB salary/statutory policy, and nothing here proposes a new generic framework, tax abstraction,
payment integration, Form 16/filing integration, or benefits feature — per the explicit
instruction, any such area found during this review is classified **OPTIONAL / DO NOT IMPLEMENT
NOW** rather than scoped into V1.

**Headline finding**: every pipeline *stage* already has a working technical implementation.
The two genuine **MUST FIX** gaps found are not missing stages — they are two specific,
narrow, well-evidenced defects in how employees are selected into a run and how their
compensation is resolved, both of which can silently produce **zero pay** for an employee who
should have been paid. Neither requires inventing a salary or statutory policy to fix.

**Update**: item 2a below (mid-period compensation resolution) has since been **fixed** - see
`CompensationResolver`/`EmployeeCompensationRepository.findEffectiveForEmployee` and the
"Revision: mid-period compensation resolution defect fixed" entry in
`docs/PAYROLL_REQUIREMENTS.md`. Item 2b (terminated-employee run exclusion) has since been
**fixed** too - see `EmployeeClient.resolveEmployeeRefsEligibleForPeriod` and the "Revision:
terminated-employee payroll eligibility (GDB business decision Option A)" entry in
`docs/PAYROLL_REQUIREMENTS.md`. No MUST FIX gaps remain open in this report.

---

## 1. Employee Compensation

**Current implementation**: `EmployeeCompensationService`/`EmployeeCompensationController`
(`POST/GET/PATCH /api/v1/payroll/compensations[/{id}]`) — full CRUD, overlap prevention at write
time (`assertNoOverlap`), historical immutability once used by a `FINALIZED` run
(`hasBeenUsedByFinalizedRun`), full-replace update semantics, HR/Finance-only access
(`payroll.process`/`payroll.read.all`), no self-service path.

**What is missing**: Nothing in the CRUD surface itself. **However**, the *resolution* step that
reads this data during calculation has a defect — see Section 2 below, since it is really a
run-creation/resolution issue rather than a compensation-management issue.

**Classification**: ✅ **ALREADY COMPLETE** (the CRUD/write-side is solid; the read/resolution-side
defect is tracked separately in Section 2 so it isn't lost in this bucket).

---

## 2. Employee Lifecycle: Mid-Period Joiners and Leavers (cross-cutting — found during this review)

This is not one of the ten pipeline boxes, but it sits directly underneath "Employee
Compensation → Attendance..." as a precondition every run depends on, and it is the single most
consequential finding in this report.

### 2a. New/changed compensation effective mid-period is silently excluded from that period's run

**Current implementation** — `EmployeeCompensationRepository.findEffectiveForEmployee`
(`backend/payroll-service/.../repository/EmployeeCompensationRepository.java:24-31`):

```java
@Query("""
        select c from EmployeeCompensation c
        where c.employeeRef = :employeeRef
          and c.status = ...ACTIVE
          and c.effectiveFrom <= :date
          and (c.effectiveTo is null or c.effectiveTo >= :date)
        """)
```

called from `CompensationResolver.resolveEffective` (`.../calculation/CompensationResolver.java:28-30`)
with `:date = period.getStartDate()` — i.e. the *first day* of the payroll month.

**What is missing**: the query requires `effectiveFrom <= period.startDate`. Any
`EmployeeCompensation` row whose `effectiveFrom` falls *after* the period's first day — which is
true for **every new joiner hired after the 1st of the month**, and for **every compensation
revision (raise, correction, role change) that takes effect mid-month** — never resolves for that
period at all. `PayrollCalculationEngine` then records `NO_EFFECTIVE_COMPENSATION`
(`.../calculation/PayrollCalculationEngine.java:132-136`) and generates **no `PayrollRunLine`
whatsoever** for that employee that month — not a partial amount, not a flagged-but-paid line,
nothing. The exception row is indistinguishable from "this employee has no compensation
configured at all," so HR cannot even tell the two cases apart from the exception list.

Confirmed this is genuinely unhandled, not just under-tested: no existing test in
`CompensationManagementIntegrationTest`/`StatutoryRuleIntegrationTest`/`PayrollIntegrationTest`
exercises a compensation record whose `effectiveFrom` falls inside (rather than before) the
period being processed.

The resolver's own Javadoc already names this precisely: *"compensation revisions taking effect
mid-period are not specially handled, since no documented rule defines how to split a period
across two compensation records; this is a flagged minimum decision, not an invented policy."*
That framing was correct for *splitting* a period across two records (a genuine proration
question this report does not propose solving). But the current behavior does not just skip
*splitting* — it skips **resolving the record at all**, even though exactly one `ACTIVE` record
plainly covers part of the period and no ambiguity exists. Resolving it (using its full
configured amount, unprorated — the same "default to the safe, non-invented behavior" principle
`NoOpProrationPolicy` already applies elsewhere) is a narrower, lower-risk fix than deciding a
split/proration formula, and does not invent any salary policy — it only changes *which existing
record* is selected for a period that record demonstrably overlaps.

**Classification**: ✅ **FIXED** (was 🔴 MUST FIX for Payroll V1). `findEffectiveForEmployee` now
resolves by effective-range overlap with the full period
(`effectiveFrom <= periodEnd` and `effectiveTo` null-or-`>= periodStart`), so a record becoming
effective anywhere within the period resolves correctly. `EmployeeCompensationService.
hasBeenUsedByFinalizedRun` was updated identically for consistency. Regression tests cover before/
exactly-on/during/after the period, plus the existing overlap-ambiguity rule is preserved (see
`docs/PAYROLL_REQUIREMENTS.md`'s "Revision: mid-period compensation resolution defect fixed").

### 2b. A terminated (now-inactive) employee can never again be included in a payroll run

**Previous implementation** — `PayrollRunService.create`
(`.../service/PayrollRunService.java:100-115`) snapshotted the run's employees via
`employeeClient.resolveActiveEmployeeRefs()` at run-creation time. `EmployeeClient.
resolveActiveEmployeeRefs()` (`.../client/EmployeeClient.java:40-67`) called Employee Service's
`GET /api/v1/employees?status=ACTIVE` — **only** currently-`ACTIVE` employees were ever returned;
there was no alternative method in `EmployeeClient` for "employees active at any point during a
given period" or "recently terminated employees still owed final pay."

**What was missing**: once Employee Service marked an employee `INACTIVE` (resignation/termination
effective date reached), that employee could **never again** appear in any future `PayrollRun`'s
employee snapshot — including the run for the very month they worked before leaving. Since payroll
for a month is normally run *after* that month ends (by which time a mid-month leaver is already
inactive), this was not an edge case — it was the **normal timing** for anyone who leaves mid-month.
The only existing workaround, an `ADJUSTMENT` run (Section K), only works against an *already-
finalized* original run that included the employee while still active — which requires the
original run to have been created before the employee went inactive, an ordering that will not
hold for a same-month departure.

**Classification**: ✅ **FIXED** (was 🔴 MUST FIX for Payroll V1), per the approved GDB business
decision (**Option A**: a terminated employee remains eligible for the payroll period in which
they worked; their final month's salary is processed through the normal payroll run — no separate
Final Settlement module). `EmployeeClient` gained
`resolveEmployeeRefsEligibleForPeriod(periodStart, periodEnd)`, which returns every currently
`ACTIVE` employee (unchanged) plus any `INACTIVE` employee whose existing employment record
(Employee Service's already-existing `Employment.startDate`/`endDate`, read via the already-existing
`GET /employees?status=INACTIVE` and `GET /employees/{id}` endpoints — no new Employee Service
capability, no cross-service database access) overlaps the period:
`startDate <= periodEnd AND (endDate IS NULL OR endDate >= periodStart)`. Both `PayrollRunService.
create` and `.createAdjustment` (the shared employee-snapshot mechanism) now call this method
instead of the ACTIVE-only one. No LOP/proration formula, PF/ESI/PT/TDS calculation, payslip
generation, or adjustment-run-specific rule was changed — only which employees are selected into a
run's snapshot. See `docs/PAYROLL_REQUIREMENTS.md`'s "Revision: terminated-employee payroll
eligibility (GDB business decision Option A)" for the full change log.

---

## 3. Attendance + Approved Leave

**Current implementation**: `AttendanceEventListener`/`LeaveEventListener` consume
`attendance.finalized.v1`/`leave.approved.v1` via the standard inbox/`ProcessedEvent` pattern into
`PayrollAttendanceInput`/`PayrollLeaveInput`. Attendance input is correctly period-filtered at
read time (`PayrollAttendanceInputRepository.findByEmployeeRefAndWorkDateBetween`, called from
`PayrollCalculationEngine.calculate`). Only `FINALIZED` attendance and approved leave are ever
persisted (re-checked defensively for attendance; structurally guaranteed for leave, since
`leave.approved.v1` is only ever published on approval).

**What is missing**: `PayrollLeaveInput` carries no date-range field (Leave Service's event does
not emit one), so `PayrollLeaveInputRepository.findByEmployeeRef` (used in the calculation engine)
returns **every** approved-leave snapshot for an employee ever recorded, with no period filter.
This is already documented as a known data-availability gap, not an invented assumption. It has
no visible effect today because the only registered `ProrationPolicy` (`NoOpProrationPolicy`)
never reads leave data at all — but it would need to be resolved (by Leave Service adding a date
range to its event, out of this service's control) before any real proration formula could
correctly attribute leave to the right month.

**Classification**: ✅ **ALREADY COMPLETE** for V1 purposes (the unfiltered-leave-snapshot issue
is real but currently inert, and fixing it is moot until a proration formula exists to consume it
— see Section 5).

---

## 4. Earnings

**Current implementation**: `CalculationStrategyRegistry` resolves each `EARNING`-type
`CompensationComponent` through `FixedAmountStrategy` (a configured, fixed amount) or, since the
Rule Engine extension, a `StatutoryRule`-backed strategy if `calculationStrategyCode` names one.
Earnings are summed into `grossPay` in `PayrollCalculationEngine.calculate`.

**What is missing**: nothing structural. Earnings are a configured amount per the "Common India
Payroll V1 Baseline" — there is no concept of hourly/attendance-derived pay (e.g. overtime
computed from hours worked), but this was never a documented requirement and inventing one now
would itself be inventing a GDB pay policy.

**Classification**: ✅ **ALREADY COMPLETE**.

---

## 5. LOP/Proration

**Current implementation**: `ProrationPolicyRegistry` resolves `CompensationComponent.
prorationPolicyCode` to a registered `ProrationPolicy`; the only one registered,
`NoOpProrationPolicy`, always returns zero adjustment (full pay), regardless of attendance/leave
input. The technical seam is real: `ComponentCalculationContext` already carries the employee's
period-filtered attendance inputs and (unfiltered - Section 3) leave inputs to any future policy
without any pipeline change.

**What is missing**: an actual loss-of-pay/proration formula. This is explicitly out of scope to
invent — PAYROLL_REQUIREMENTS.md Section H and every prior revision of this document have
consistently left it PENDING_GDB_APPROVAL, and no formula (per-day rate basis, rounding rule,
which leave types count, etc.) can be chosen without a GDB/Finance policy decision.

**Classification**: ⚪ **OPTIONAL / DO NOT IMPLEMENT NOW** — the technical seam is complete and
correct; the formula itself is a business decision this task explicitly forbids inventing.

---

## 6. Applicable PF/ESI/PT/TDS

**Current implementation**: the Configurable Statutory + Tax Rule Engine
(`StatutoryRule`/`StatutoryRuleService`/`StatutoryRuleCalculationStrategy`) supports
`FIXED_AMOUNT`/`PERCENTAGE`/`THRESHOLD_BASED` (sufficient for PF/ESI) and
`SLAB_BASED`/`PROGRESSIVE_TAX` (sufficient in shape for Professional Tax's bracket structure and
TDS's marginal-rate calculation), with jurisdiction-aware and tax-regime-aware resolution. A
component with no resolvable rule contributes zero and raises a `PayrollException`
(`MISSING_STATUTORY_RULE`/`TAX_CONFIGURATION_REQUIRED`/`STATUTORY_RULE_NOT_APPLICABLE`/
`MISSING_TAX_CONFIGURATION`) rather than blocking the run or fabricating an amount.
`EmployeeStatutoryProfile` correctly never infers `NOT_APPLICABLE` from a missing identifier.

**What is missing**: every real PF/ESI/Professional Tax/TDS rate, wage ceiling, slab boundary,
and tax-regime value — the `statutory_rules` table is, and must remain, empty until GDB/Finance/
Legal supplies and confirms them (`docs/INDIA_PAYROLL_V1_RULE_SOURCES.md`/
`docs/INDIA_PAYROLL_V1_RULE_CONFIGURATION.md`). Additionally, two further technical refinements
were already identified and explicitly deferred: Professional Tax's half-yearly-vs-monthly
apportionment, and TDS's rebate/cess/surcharge chaining and annual projection. Per this task's
explicit instruction not to add further tax abstractions, **neither is scoped into V1**.

**Classification**: ⚪ **OPTIONAL / DO NOT IMPLEMENT NOW** for the remaining TDS/PT refinements
(explicitly excluded by this task). The rate/value gap is **not a code task at all** — it is
blocked on GDB/Finance/Legal input, tracked separately, and no code change can close it.

---

## 7. Gross/Deductions/Net Pay

**Current implementation**: `PayrollCalculationEngine.calculate` sums each component into
`grossPay`/`totalDeductions`/`totalEmployerContributions`, computes `netPay = grossPay -
totalDeductions` (employer contributions correctly excluded from net pay), and persists one
immutable `PayrollRunLine` per employee with a structured JSON breakdown for full traceability.
Reprocessing before finalization is idempotent (delete-and-recreate, backed by a DB uniqueness
constraint); a genuine calculation failure rolls back every write and is safely recorded as
`CALCULATION_FAILED`.

**What is missing**: nothing, **except** that this stage inherits both Section 2 defects — an
employee who should have a line (mid-period joiner/changed compensation, or a recent leaver) may
simply not get one.

**Classification**: ✅ **ALREADY COMPLETE** (defects affecting *which employees* reach this stage
are tracked in Section 2, not here).

---

## 8. Approval

**Current implementation**: `PayrollRunService.submitForApproval`/`approve`/`reject` enforce the
`CALCULATED → PENDING_APPROVAL → APPROVED`/`REJECTED` transitions, gated by `payroll.process`
(submit) and `payroll.approve` (approve/reject), with `assertNotSelfApproval` rejecting any
attempt by the identity recorded as the run's own `initiatedBy` — including for `ADJUSTMENT` runs,
since nothing branches on `runType`.

**What is missing**: nothing found.

**Classification**: ✅ **ALREADY COMPLETE**.

---

## 9. Finalization

**Current implementation**: `PayrollRunService.finalizeRun` transitions `APPROVED → FINALIZED`
(self-approval-guarded), publishes `payroll.processed.v1` via the transactional outbox, and
triggers payslip generation in a separate transaction so a payslip failure never reopens or rolls
back the `FINALIZED` state. Re-invoking finalize on an already-`FINALIZED` run is the documented,
safe retry path (only still-missing payslips are retried; no duplicate event).

**What is missing**: nothing found.

**Classification**: ✅ **ALREADY COMPLETE**.

---

## 10. Payslip

**Current implementation**: `PayslipGenerationService` renders one PDF per `PayrollRunLine` via
`PayslipPdfGenerator` (company header, employee info, earnings/deductions, employer contributions,
gross/total-deductions/net totals, amount in words, YTD summary, applicable-tax section, footer)
and uploads it through Document Service's workload-upload contract, storing only the
`document_ref`. `payslip.generated.v1` is published once per payslip with an ID-only payload.
`PayslipContentAssembler` reads the `PayrollRunLine` breakdown generically by component code, so
any future PF/ESI/PT/TDS line computed by the rule engine will appear on the payslip with **no
further code change** — confirmed by inspection, not assumed.

**What is missing**: nothing found. "Not available"/"Not configured" is correctly displayed
wherever employee-profile or tax data is genuinely absent, never fabricated.

**Classification**: ✅ **ALREADY COMPLETE**.

---

## 11. Employee Download

**Current implementation**: `PayslipService.download` (`GET /payroll/payslips/{id}/download`)
resolves "self" strictly server-side via Employee Service (`PayslipAccessGuard.resolveSelf`),
authorizes via `payslip.read.self`/`payslip.read.all` with no team-scoped tier, and returns a
short-lived, already-secured Document Service download reference — never a public or
long-lived URL. Self-access and HR/Finance access are audited distinctly (`viewerRole`).

**What is missing**: nothing found.

**Classification**: ✅ **ALREADY COMPLETE**.

---

## Summary table

| # | Stage | Status |
|---|---|---|
| 1 | Employee Compensation (CRUD) | ✅ ALREADY COMPLETE |
| 2a | Mid-period compensation resolution | ✅ **FIXED** |
| 2b | Terminated-employee run exclusion | ✅ **FIXED** |
| 3 | Attendance + Approved Leave | ✅ ALREADY COMPLETE |
| 4 | Earnings | ✅ ALREADY COMPLETE |
| 5 | LOP/Proration | ⚪ OPTIONAL / blocked on GDB formula |
| 6 | Applicable PF/ESI/PT/TDS | ⚪ OPTIONAL (refinements) / blocked on GDB values |
| 7 | Gross/Deductions/Net Pay | ✅ ALREADY COMPLETE |
| 8 | Approval | ✅ ALREADY COMPLETE |
| 9 | Finalization | ✅ ALREADY COMPLETE |
| 10 | Payslip | ✅ ALREADY COMPLETE |
| 11 | Employee Download | ✅ ALREADY COMPLETE |

---

## The one highest-priority implementation task

~~Fix compensation resolution (`EmployeeCompensationRepository.findEffectiveForEmployee` /
`CompensationResolver`) so that a payroll period resolves any `ACTIVE` compensation record whose
effective range *overlaps* the period — not only one whose `effectiveFrom` is on or before the
period's first day.~~ **Done** — see Section 2a above and
`docs/PAYROLL_REQUIREMENTS.md`'s "Revision: mid-period compensation resolution defect fixed."

~~The new highest-priority task is Section 2b: give a terminated/inactive employee a way to be
included in the payroll run for their final working month.~~ **Done** — see Section 2b above and
`docs/PAYROLL_REQUIREMENTS.md`'s "Revision: terminated-employee payroll eligibility (GDB business
decision Option A)."

**No MUST FIX gap remains open for Payroll V1's mandatory flow.** Both items this report
identified (2a mid-period compensation resolution, 2b terminated-employee run exclusion) are now
fixed and covered by regression tests. The only remaining open items in this report are the
explicitly OPTIONAL ones already classified above and intentionally left unimplemented per this
task's scope: the LOP/proration formula (Section 5) and the real PF/ESI/PT/TDS rates/values plus
their TDS/PT refinements (Section 6) — both are blocked on a GDB/Finance/Legal decision, not on
any further code change.
