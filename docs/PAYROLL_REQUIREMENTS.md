# Payroll Service — Implementation-Ready Specification

This document locks the technical/product decisions listed below for Payroll Service and
defines the concrete specification needed to begin implementation. It builds on, and does not
contradict, `docs/DEVELOPMENT_ROADMAP.md`, `docs/architecture/MICROSERVICES.md`,
`docs/database/DATABASE.md`, `docs/api/API.md`, `docs/security/RBAC.md`,
`docs/architecture/COMMUNICATION.md`, `docs/ARCHITECTURE_REVIEW.md`, `docs/DECISIONS.md`,
`docs/workflows/WORKFLOWS.md`, and `docs/PAYROLL_REPORTING_DECISIONS.md`.

**Status: Phase 1 (Foundation), Phase 2 (Calculation Core), Phase 3 (Approval/Finalization),
Phase 4 (Payslip Generation), Adjustment Runs (Section K), and Employee Compensation Management +
Statutory Profile + Payroll Exceptions (Section G/E) are all implemented at the *technical* level.
Every sensitive business function - real salary/tax/statutory values, the actual pay-component
catalogue, the adjustment/netting accounting policy, and every other item in Section X - remains
gated on GDB approval.**
A `payroll-service` module implements `PayrollPeriod`/`PayrollRun` lifecycle (Section D, now
including `PROCESSING`/`CALCULATION_FAILED`), RBAC (`payroll.process`/`payroll.approve`/
`payroll.read.all`), audit logging, idempotency, **and** a real calculation pipeline: effective-dated
compensation resolution, configurable calculation strategies for earnings/deductions/employer
contributions (Section H), configurable (no-op) proration, consumption of `attendance.finalized.v1`/
`leave.approved.v1` into Payroll's own input snapshots (Section I), `PayrollRunLine` results, and
`PayrollException` records for employees with no effective compensation (Section E/F). **No
statutory rate, tax slab, threshold, eligibility rule, seeded pay-component catalogue row, or
GDB-specific salary policy is implemented anywhere** - every calculation strategy registered today
resolves to a fixed, already-configured amount or a zero adjustment; no formula exists in code.
Every item in Section X remains PENDING_GDB_APPROVAL and unresolved by this code, including the
no-compensation-employee handling question (block vs. skip), which is technically surfaced as a
`PayrollException` without resolving the underlying business question. Every other architecture
document's "(deferred)" marking for Payroll now means specifically "calculation *content* and
sensitive business functionality deferred," not "no code exists" - see each document's own Payroll
row for the precise split.

## Decisions locked by this document

1. India payroll architecture baseline.
2. INR as the initial payroll currency.
3. Monthly payroll periods.
4. Effective-dated employee compensation.
5. Payroll Service owns payroll orchestration/calculation (not an external provider).
6. Finalized attendance and approved leave are payroll inputs.
7. Payroll runs require approval before finalization.
8. Finalized payroll must never be edited directly.
9. Corrections must use adjustment/reprocessing runs.
10. Employee payslip access is self-only.
11. Payslips are detailed and professionally structured, comparable in feature depth to modern
    payroll systems such as Zoho Payroll, but not copied in UI/branding.
12. Payslips support: company branding, employee details, payroll period, pay date, earnings,
    deductions, employer contributions, gross pay, total deductions, net pay, amount in words,
    YTD information, applicable tax information, PDF generation, print, secure download, and
    payslip history.
13. Payroll files use Document Service rather than introducing separate payroll object storage.
14. Payroll data is highly sensitive and requires enhanced audit/security controls.
15. Payroll processing must be idempotent.
16. Payroll events must be versioned and auditable.

Items not in this list (salary structure, pay component catalogue, deductions, benefits,
statutory rules, TDS configuration, provider, exact HR/Finance authority split, retention
period, exact reporting requirements) are explicitly **PENDING_GDB_APPROVAL** — see Section X.
No value or rule for these was invented anywhere in this document.

## Common India Payroll V1 Baseline

**This is a provisional, configurable product-design baseline — not GDB-specific legal or
statutory policy.** It names and consolidates the thirteen structural patterns already locked
above/below into a single reference label ("the Baseline") that later phases and documents can
cite by name instead of re-deriving from the decision list. "V1" signals this is one versioned,
swappable baseline (a future baseline could target a different jurisdiction, currency, or
compliance model) — no V2 is designed or implied here, and adopting V1 commits to nothing beyond
it. Establishing this label **resolves zero Section X items**: every statutory rate, tax slab,
threshold, eligibility rule, and GDB-specific salary policy remains exactly as
PENDING_GDB_APPROVAL as before. The Baseline governs shape/structure only, never a value.

| # | Baseline element | Locked as | Defined in |
|---|---|---|---|
| 1 | Monthly payroll | Decision 3 — no other frequency is supported or coded | Section D |
| 2 | INR | Decision 2 — the only currency `EmployeeCompensation.currency` accepts | Section F/G |
| 3 | Structured salary components | `CompensationComponent`/`PayComponent` with a fixed type vocabulary (`EARNING`/`DEDUCTION`/`EMPLOYER_CONTRIBUTION`); `component_code` and every rate/amount are configurable data, never a hard-coded component | Section E/F/G |
| 4 | Configurable statutory deductions | Statutory deductions are ordinary `DEDUCTION`-type components identified by a free-form `component_code` — no deduction name, rate, slab, or eligibility rule is hard-coded anywhere; the actual statutory catalogue is PENDING_GDB_APPROVAL | Section F/G, Section X |
| 5 | Attendance/approved leave inputs | Decision 6 — only `FINALIZED` attendance and `APPROVED` leave are ever read | Section I |
| 6 | Configurable LOP/proration | The pluggable `ProrationPolicy` identifier, defaulting to a no-op (full pay) until a real formula is supplied — no per-day/LOP formula is hard-coded | Section H |
| 7 | Effective-dated salary revisions | Decision 4 — `EmployeeCompensation.effective_from/effective_to`; a revision is a new row, never an in-place edit | Section G |
| 8 | Maker-checker approval | Decisions 7 — distinct `payroll.process`/`payroll.approve` permissions plus service-layer self-approval prevention | Section J |
| 9 | Immutable finalization | Decision 8 — no code path updates a `FINALIZED` run | Section D |
| 10 | Adjustment runs | Decision 9 — corrections are a new `PayrollRun` with `run_type = ADJUSTMENT`, never an edit to the original | Section K |
| 11 | Detailed employee payslip | Decisions 11–12 — full field list (branding, earnings/deductions/employer contributions, gross/net, amount in words, YTD, tax field, PDF, secure download, history) | Section L/M |
| 12 | Document Service storage | Decision 13 — `Payslip.document_ref` only, no payroll-owned object storage | Section N/U |
| 13 | Employee self-only payslip access | Decision 10 — no team-scoped payslip tier exists | Section B/S |

**Explicitly excluded from the Baseline** (restated, not newly decided — see Section X for the
authoritative pending list): statutory rates, tax slabs, thresholds and eligibility (PF/ESI/PT/
TDS or any other), the actual pay-component catalogue, benefits, the real proration/LOP formula,
adjustment/netting accounting treatment, the HR/Finance authority split, retention periods, the
payment/disbursement provider, and exact reporting requirements. None of these gates is loosened,
removed, or assumed by naming the Baseline; each remains PENDING_GDB_APPROVAL until GDB/Finance/
Legal supplies it, and calculation itself (Phase 2 onward, Section Y) remains gated regardless of
this Baseline existing.

### Revision: technical gap resolution pass

This revision resolves five purely technical gaps the original specification had flagged but
left open, without changing any of the sixteen locked business/product decisions above and
without inventing any salary amount, tax rule, statutory percentage, compensation policy,
retention period, or provider choice:

1. **Attendance/leave proration** — a configurable proration policy interface is now defined
   (Section H); no formula is chosen.
2. **Maker-checker approval** — a new permission, `payroll.approve`, is introduced, distinct
   from `payroll.process` (Section J/O/P). This is a required addition to `RBAC.md`'s
   permission catalogue, not yet made there — tracked in Section X.
3. **Correction/adjustment netting** — the technical model for signed adjustment lines
   referencing the original run is defined (Section K); the accounting/netting treatment
   remains business-pending.
4. **Payslip-ready event** — a new event, `payslip.generated.v1` (`PAYSLIP_GENERATED`), is
   defined alongside the unchanged `PAYROLL_PROCESSED` (Section Q). This is a required
   addition to `COMMUNICATION.md`'s event table, not yet made there — tracked in Section X.
5. **Document Service integration contract** — a full service-to-service contract is defined
   (Section N/U), grounded in `docs/security/SECURITY.md`'s already-documented workload-identity/
   OAuth2-client-credentials pattern. This requires a new Document Service endpoint, not
   implemented here — tracked in Section X as a required cross-service contract change.

A sixth, previously-unflagged technical ambiguity was found and resolved while reviewing the
document for this pass: which employees are included in a payroll run (Section I).

### Revision: source-of-truth documentation synchronization

A follow-up pass applied the three cross-document changes items 2, 4, and 5 above had
identified but not yet made: `docs/security/RBAC.md` now defines `payroll.approve` and the
maker-checker/self-approval rule; `docs/architecture/COMMUNICATION.md` now documents
`payslip.generated.v1` (`PAYSLIP_GENERATED`) in its event contract table; and
`docs/api/API.md`/`docs/architecture/MICROSERVICES.md` now document the Document Service
workload-upload contract. No business decision was made or changed by this pass.

### Revision: Document Service workload-upload endpoint implemented

`POST /api/v1/documents/workload-uploads` and the `workload.document.upload` authority
described in Section N/U are **now implemented in Document Service** (34/34 Document Service
tests passing, including dedicated workload-upload/quarantine/idempotency/authorization-
regression tests). This closes the one remaining engineering blocker Section X previously
tracked for Payroll's own Phase 4 — see Section X for the current status. No Payroll Service
code was written; this pass touched Document Service only, per its own explicit task scope.

### Revision: Payroll Phase 1 (Foundation) implemented

A `payroll-service` module now exists (see this document's own Status line above and Section Y).
It implements exactly the "Foundation" phase Section Y describes: `PayrollPeriod` creation/
retrieval (monthly periods only, decision 3), `PayrollRun` creation with an immutable
active-employee snapshot (Section I) and the full Section D lifecycle (`DRAFT → CALCULATED →
PENDING_APPROVAL → APPROVED → FINALIZED`, with `REJECTED` re-entering processing), the
`payroll.process`/`payroll.approve` maker-checker split with self-approval prevention (Section
J), audit logging of every transition (Section R), idempotent run creation/processing (Section
T), and `PAYROLL_PROCESSED` emission on finalize (ID/period/employee-count only, no amount -
Section Q). `EmployeeCompensation`/`CompensationComponent`/`PayComponent` tables exist as empty
schema (no REST API - none is documented in Section O) so Phase 2's calculation pipeline has
somewhere to read from once the catalogue is approved.

**Nothing else changed.** No `PayrollRunLine`, no `Payslip`, no calculation, no pay-component
catalogue row, no compensation amount, no proration formula, no tax/statutory logic, no
Document Service integration, and no payslip endpoint were implemented. Every business decision
in Section X remains exactly as PENDING_GDB_APPROVAL as before this revision - this pass resolved
zero of them. The "Phase 1 cannot begin coding" caveat that previously closed Section Y is
revised in Section Y below: it was about the pay-component *catalogue's content*, which Section F
already fully specified the *shape* of independent of that content, so the technical foundation
could be (and has been) built without it.

### Revision: Common India Payroll V1 Baseline established

Added the "Common India Payroll V1 Baseline" section above, naming and consolidating the
thirteen structural patterns the sixteen locked decisions and Sections D–U already define
(monthly periods, INR, structured salary components, configurable statutory deductions,
attendance/leave inputs, configurable LOP/proration, effective-dated revisions, maker-checker
approval, immutable finalization, adjustment runs, detailed payslip, Document Service storage,
self-only payslip access) into a single citable label for later phases to reference. This is a
naming/organizing pass only: it locks no new decision, resolves no Section X item, and hard-codes
no statutory rate, tax slab, threshold, eligibility rule, or GDB-specific salary policy. The
Baseline is explicitly framed as a provisional, versioned, configurable product default — not
GDB legal policy - and calculation remains exactly as gated as before this revision.

### Revision: Payroll Phase 2 (Calculation Core) implemented

Payroll's real calculation pipeline (Section H) is now implemented against the Common India
Payroll V1 Baseline, entirely configuration-driven and with **zero statutory rate, tax slab,
threshold, eligibility rule, or GDB-specific salary policy anywhere in code**:

- **Effective-dated compensation resolution** (Section G) - `CompensationResolver` selects the
  `EmployeeCompensation` covering the period's start date; historical records are never
  overwritten.
- **Configurable calculation strategies** (Section H) - `ComponentCalculationStrategy`, with
  `StatutoryCalculator`/`TaxCalculator` as distinct marker extension points for statutory/tax
  components specifically, resolved per component via a new `calculation_strategy_code` column
  (Section F). Only one strategy is registered (`FixedAmountStrategy` - returns the component's
  own configured amount unchanged); no formula exists.
- **Configurable (no-op) proration** (Section H) - the pluggable `ProrationPolicy` design from
  the technical-gap-resolution revision is now implemented; `NoOpProrationPolicy` is the only
  registered policy.
- **`PayrollRunLine` results** (Section E/F) - one immutable row per employee per run: gross pay,
  total deductions, total employer contributions (a new aggregate column, added because Section M
  requires employer contributions as a payslip field), net pay, and a structured JSON breakdown.
- **`PayrollException` records** (Section E/F, new entity) - an employee with no effective
  compensation for the period gets an exception row instead of a line; the run still reaches
  `CALCULATED`. The underlying business question (block the run vs. silently skip) remains
  exactly as unresolved in Section X as before - this only makes the *symptom* visible and
  auditable.
- **Attendance/leave event consumption** (Section I) - `attendance.finalized.v1`/
  `leave.approved.v1` are now actually consumed (queue/DLX/DLQ + inbox pattern) into
  `PayrollAttendanceInput`/`PayrollLeaveInput` snapshots (Section F, new entities).
- **New lifecycle states** (Section D) - `PROCESSING` (separately committed, visible for the
  duration of calculation) and `CALCULATION_FAILED` (a failed attempt is safely recorded, never
  silently lost, and is reprocessable exactly like DRAFT/REJECTED) were added to the state
  machine. This is a technical addition beyond the original design, made necessary by
  implementing real calculation; it changes no business decision.
- **Idempotency** (Section T) - reprocessing deletes and recreates lines/exceptions rather than
  accumulating them, backed by a `(run_id, employee_ref)` uniqueness constraint on both tables.
- **Calculation failure/rollback** - an unhandled error during calculation rolls back every write
  from that attempt (no partial `PayrollRunLine`), and the run is marked `CALCULATION_FAILED` in
  a separate, already-committed transaction so the failure itself is never lost.

**Nothing else changed.** No `Payslip`, no PDF, no Document Service integration from Payroll, no
real tax/statutory/deduction formula, no seeded catalogue *content* (only catalogue *names*, per
the Baseline), no payment/disbursement provider, and no Reporting integration were implemented.
Every item in Section X remains exactly as PENDING_GDB_APPROVAL as before this revision, including
the no-effective-compensation business question, the real proration/LOP formula, and the
adjustment/netting accounting policy - this revision resolves none of them, only their *technical*
surfacing (exceptions, pluggable policy points).

### Revision: Payroll Payslip Generation implemented

Payroll's payslip generation and employee payslip access (Sections L/M/N/O) are now implemented,
finishing the pipeline Phase 2's own `PayrollRunLine` results already fed into:

- **`Payslip`/`PayslipGenerationFailure` entities** (Section E/F) - one immutable `Payslip` per
  `(run_id, employee_ref)`, generated only on finalization; a per-employee generation failure is
  recorded separately without blocking other employees or the run's own `FINALIZED` status.
- **PDF generation** (Section M) - every documented field (branding, employee identity,
  earnings/deductions/employer-contribution breakdown, gross/net, amount-in-words, YTD, tax field,
  period, generated timestamp) rendered from the real `PayrollRunLine`/`PayrollPeriod` data; no
  statutory/tax value is computed, only displayed as "not configured" where no real rule exists.
- **Document Service integration** (Section N/U) - `PayslipGenerationService` calls the
  already-implemented workload-upload contract (`createWorkloadUpload` → upload real PDF bytes →
  `completeUpload`), storing only the resulting `document_ref`; Payroll never stores a payslip
  binary itself.
- **`payslip.generated.v1`** (Section Q) - published once per `Payslip` row via the existing
  transactional outbox, payload limited to IDs/timestamp only.
- **Self-service access** (Section B/O) - `GET /payroll/payslips/me` (filterable by period/
  financial year, paginated), `GET /payroll/payslips/{id}`, `GET /payroll/payslips/{id}/download`,
  gated by `payslip.read.self`/`payslip.read.all` with server-side ownership resolution (never a
  client-supplied identity).
- **Finalization retry** - re-invoking `POST /payroll/runs/{id}/finalize` on an already-`FINALIZED`
  run is the documented retry mechanism: no re-transition, no duplicate `payroll.processed.v1`,
  only still-missing payslips are (re)attempted.

**Nothing else changed.** No real tax/statutory/PF/ESI/TDS value, no payment/disbursement
provider, and no Reporting integration were implemented. Every item in Section X remains exactly
as PENDING_GDB_APPROVAL as before this revision. A known, narrow limitation: Document Service's
own object storage tracks only an opaque reference with no object-existence lookup by checksum, so
a crash between document creation and `Payslip` row persistence has no automatic reconciliation
path today - it is recorded as a `PayslipGenerationFailure` rather than silently lost, but manual
reconciliation may be required in that specific window.

### Revision: Payroll Adjustment Runs implemented

`POST /payroll/runs/{id}/adjustments` (Section K) is now implemented, gated by `payroll.process`.
See Section K above for the full technical detail (original-run validation and immutability,
`corrects_run_id`, reuse of the identical calculation/approval/finalization/payslip pipeline,
in-flight-only idempotency, independent adjustment payslips, and the documented limitation around
correcting historical compensation). **Nothing else changed.** The accounting/netting treatment
for adjustment runs remains exactly as PENDING_GDB_APPROVAL as before this revision - this pass
resolves none of it, only the technical mechanics of creating and processing the adjustment run
itself.

### Revision: Employee Compensation Management, Statutory Profile, and extended Payroll
Exceptions implemented

Management APIs for `EmployeeCompensation`/`CompensationComponent`/`PayComponent` (previously
empty schema with no REST API, per the Phase 1 revision above), a new `EmployeeStatutoryProfile`
entity, and an extended `PayrollException` reason/status model are now implemented, per Section
G/E. **No salary amount, pay-component catalogue content, statutory rate, threshold, or
eligibility rule is introduced anywhere** — every new field is structural (dates, status,
identifiers, references), never a value:

- **Compensation CRUD** (Section G) — HR/Finance (`payroll.process` for writes, `payroll.process`
  or `payroll.read.all` for reads — no new permission) can create, retrieve, list, and update an
  `EmployeeCompensation` record together with its `CompensationComponent` lines in one request
  (full-replace semantics on update). There is **no self-service endpoint of any kind** for this
  data — no employee token can read or write their own or anyone else's compensation.
- **Overlap prevention at write time** — a new record whose `[effectiveFrom, effectiveTo]` range
  overlaps an existing `ACTIVE` record for the same employee is rejected (409) before it can ever
  be persisted. This is a write-time guard, not a change to the calculation engine's existing
  ambiguity handling (Section H still fails the whole run if ambiguous data somehow exists) —
  the guard is what keeps that case from arising going forward.
- **Historical immutability** — once a compensation record has actually been read by a
  `FINALIZED` run (derived from existing `PayrollRunLine`/`PayrollRun`/`PayrollPeriod` data, not a
  new tracking column), it can no longer be updated; attempting to do so is rejected (409).
  Records never yet used by a finalized run remain updatable.
- **Pay-component catalogue management** — `PayComponent` gains an `active` flag and full audit
  columns; HR/Finance can create/retrieve/list/update catalogue entries (code, name, type,
  active status). `CompensationComponent.componentCode` must reference an existing, active
  `PayComponent` of the matching type, or the request is rejected (422). Still only the Common
  India Payroll V1 Baseline's generic names/types — no rate, amount, or real GDB catalogue.
- **Employee statutory profile** (new entity) — one record per employee holding PF/EPF, ESI, and
  Professional Tax applicability status (`APPLICABLE`/`NOT_APPLICABLE`/`PENDING_VERIFICATION`),
  identifiers (UAN, PF member ID, ESI identifier, PT jurisdiction), and each scheme's effective
  dates. **A missing identifier is never inferred as `NOT_APPLICABLE`** — only an explicit status
  change does that; a missing identifier while status is `APPLICABLE` or `PENDING_VERIFICATION`
  is a data-quality gap, surfaced as a `PayrollException` (below), never silently assumed away.
  Managed by the same `payroll.process`/`payroll.read.all` permissions; no self-service path.
- **Extended `PayrollException` model** — the reason vocabulary now also includes
  `MISSING_STATUTORY_PROFILE`, `MISSING_PF_IDENTIFIER`, `MISSING_ESI_IDENTIFIER`,
  `INVALID_EFFECTIVE_DATES`, `OVERLAPPING_COMPENSATION`, `INVALID_PAY_COMPONENT`, and
  `OTHER_CONFIGURATION_ERROR`, alongside the pre-existing `NO_EFFECTIVE_COMPENSATION`
  (functionally the same condition the task calls "missing compensation" — not renamed, to avoid
  a breaking change to existing data/tests). Every exception now carries a `status`
  (`OPEN`/`RESOLVED`) and a resolution actor/timestamp once resolved; `GET
  /payroll/exceptions` and `POST /payroll/exceptions/{id}/resolve` expose this. **Resolution is
  record-keeping only** — marking an exception resolved does not alter any `PayrollRunLine`,
  retrigger calculation, or change whether the run was/is blocked; whether any exception type
  *should* block a run remains exactly as undecided as the pre-existing
  `NO_EFFECTIVE_COMPENSATION` business question (Section X).
- **Calculation engine integration** (Section H) — after successfully writing each employee's
  `PayrollRunLine`, the engine now additionally checks that employee's statutory profile and
  records `MISSING_STATUTORY_PROFILE`/`MISSING_PF_IDENTIFIER`/`MISSING_ESI_IDENTIFIER`/
  `OTHER_CONFIGURATION_ERROR` (for a missing PT jurisdiction) exceptions as needed — purely
  additive visibility alongside the line, never a replacement for it and never a new reason to
  block the run. `OVERLAPPING_COMPENSATION`/`INVALID_EFFECTIVE_DATES`/`INVALID_PAY_COMPONENT` are
  defined in the reason enum but are enforced at write time by the new compensation/pay-component
  services (above) rather than ever being raised by the calculation engine itself, since the
  states they describe can no longer be persisted — this is a deliberate symmetry, not an
  unfinished feature.
- **Database** — one new Flyway migration (`V4`) adds `employee_compensations.status`, six new
  `pay_components` columns (`active` + the standard audit columns), the new
  `employee_statutory_profiles` table, and `payroll_exceptions.{status,resolved_at,resolved_by}`;
  it also relaxes `payroll_exceptions`' uniqueness from `(run_id, employee_ref)` to `(run_id,
  employee_ref, reason)`, since one employee can now legitimately have more than one open
  exception reason in the same run.

**Nothing else changed.** No real salary amount, pay-component catalogue content, statutory rate,
threshold, eligibility rule, TDS rule, benefit, real proration/LOP formula, adjustment/netting
accounting policy, or HR-vs-Finance authority split was introduced or decided by this revision —
every item in Section X remains exactly as PENDING_GDB_APPROVAL as before. The existing
calculation pipeline, approval/finalization flow, adjustment-run mechanics, and payslip generation
are unchanged beyond the single additive statutory-profile-gap check described above.

---

## A. Payroll scope

Payroll Service owns payroll period definition, employee compensation records, payroll run
processing/calculation, approval and finalization, correction via adjustment runs, and payslip
generation/access. It does not own tax law, statutory computation rules, benefits policy, or
provider selection — those are GDB Finance/Legal inputs (Section X). It does not own employee
master data (Employee Service), attendance/leave truth (Attendance/Leave Services), or document
bytes (Document Service). This matches `MICROSERVICES.md`'s existing boundary: "Payroll period
processing and payslip access metadata... sensitive isolation."

## B. Employee-facing features

- **My Payslips** — list of the caller's own payslips, filterable by pay period and financial year.
- **Payslip History** — full self-history across periods/financial years, paginated.
- **View Payslip** — structured on-screen view of a single payslip's contents.
- **Download PDF** — short-lived, secure, authenticated download of the generated payslip PDF.
- **Print** — client-side rendering of the downloaded PDF; no separate print API is needed.
- **Pay period filtering** and **financial year filtering** on all list views.

Per decision 10, every one of these is self-only: an employee never sees another employee's
payslip through any endpoint.

## C. HR/Finance-facing features

- View payroll runs and their status across periods (`GET /payroll/runs`).
- Initiate a payroll run for a period (`payroll.process`).
- Review calculated results before approval.
- Approve or reject a pending run (`payroll.approve` — see Section J).
- Finalize an approved run (`payroll.approve`; irreversible; see Section D).
- Initiate an adjustment/reprocessing run against a finalized run (`payroll.process`; Section K).
- View/download any employee's payslip for authorized purposes (`payslip.read.all`), subject to
  enhanced audit logging (Section R).

The exact split of which of these HR can do versus which require Finance is
**PENDING_GDB_APPROVAL** (Section X) — `RBAC.md` documents that "HR... needs separately granted
payroll permission" and that Finance gets "payroll/payslip all/process," but not the authority
split between them for specific actions like approval versus finalization.

## D. Payroll lifecycle/state machine

No PayrollRun state machine exists in any current document (`DATABASE.md`'s Payroll row is the
only entity row in the entire platform with no enumerated states). This section defines one,
consistent with decisions 7–9. **Implemented** (Section Y Phase 2), with one addition beyond the
original design - an explicit, separately-committed `PROCESSING` state and a `CALCULATION_FAILED`
state so a calculation attempt is never silently lost:

```
DRAFT → PROCESSING → CALCULATED → PENDING_APPROVAL → APPROVED → FINALIZED
                 \→ CALCULATION_FAILED (reprocessable, same as DRAFT/REJECTED)
                                      \→ REJECTED (returns to DRAFT for correction and re-calculation)
DRAFT/CALCULATED/PENDING_APPROVAL → CANCELLED (before finalization only, not yet implemented - no endpoint exists)
```

- **DRAFT** — run created for a period; no calculation performed yet. **Implemented.**
- **PROCESSING** — calculation in progress; committed in its own transaction so it is visible for
  the duration of the run. **Implemented** (new state, added during Phase 2 - see Revision below).
- **CALCULATED** — per-employee earnings/deductions/employer-contributions computed from
  compensation + attendance/leave inputs via the configurable strategies in Section H; nothing is
  visible to employees yet. **Implemented** - using only fixed, pre-configured amounts and a
  no-op proration adjustment; no formula.
- **CALCULATION_FAILED** — the calculation attempt raised an unhandled error; no partial
  `PayrollRunLine`/`PayrollException` rows are left behind (the attempt's own transaction rolls
  back completely), and the run can be reprocessed exactly like DRAFT/REJECTED. **Implemented**
  (new state, added during Phase 2).
- **PENDING_APPROVAL** — submitted for the approval decision required by decision 7. **Implemented**
  (technical transition only; still gated on the business questions in Section X for who may act).
- **APPROVED** — decision recorded; run may now be finalized. **Implemented.**
- **FINALIZED** — terminal, immutable per decision 8. Payslips are generated and become visible
  to employees only at this point. **Implemented**, including payslip generation (Phase 4) - a
  finalized run's own row and `PayrollRunLine`/`Payslip` rows are never updated in place again.
- **REJECTED** — sent back for correction; a rejected run never reaches FINALIZED. **Implemented.**
- **CANCELLED** — abandoned before finalization; no payslips are ever generated. **Not
  implemented** - modeled in the status enum for schema completeness only; no endpoint reaches it.

A **FINALIZED** run's PayrollRun row and its per-employee results are never updated in place —
enforced at the service layer (no update path exists once `status = FINALIZED`) and reinforced
by not exposing any edit endpoint for a finalized run, mirroring how Workflow's `ApprovalTask`
and Document's `DocumentVersion` are already treated as append-only once decided.

## E. Payroll entities

Extends `DATABASE.md`'s documented `PayComponent; PayrollPeriod; PayrollRun; Payslip` with the
entities decisions 4–9 require to be actionable:

| Entity | Purpose | Status |
|---|---|---|
| `PayrollPeriod` | One calendar month (decision 3): year, month, start/end dates, cut-off date, status | **Implemented** (Phase 1) |
| `EmployeeCompensation` | Effective-dated compensation record per employee (decision 4): employee ref, currency (INR), effective-from/to, status (`ACTIVE`/`INACTIVE`) | **Implemented** (Phase 1 schema; Phase 2 resolution logic; CRUD + overlap prevention + historical immutability implemented in the Compensation Management revision) - no amount value seeded anywhere |
| `CompensationComponent` | A single component (earning/deduction/employer-contribution) attached to an `EmployeeCompensation`, referencing the `PayComponent` catalogue, plus a `calculation_strategy_code`/`proration_policy_code` identifying which registered strategy applies | **Implemented**; catalogue *content* (real rates/amounts) remains PENDING |
| `PayComponent` | Catalogue master (code, name, type, active flag) | **Implemented** - seeded with the Common India Payroll V1 Baseline's generic component names/types only (Basic Salary, HRA, Other Allowance, Bonus, Overtime, Other Earning, PF, ESI, Professional Tax, TDS, Loan/Advance, Other Deduction, Employer PF, Employer ESI); CRUD implemented in the Compensation Management revision; no rate, amount, or eligibility rule |
| `EmployeeStatutoryProfile` | One per-employee record of PF/ESI/Professional Tax applicability status, identifiers, and effective dates (new entity) | **Implemented** (Compensation Management revision) - no statutory rate/threshold/eligibility rule; a missing identifier is never inferred as `NOT_APPLICABLE` (Section G) |
| `PayrollRun` | One run for one period: run type (REGULAR/ADJUSTMENT), corrects-run reference, status (Section D), initiated/approved/finalized actors and timestamps | **Implemented** |
| `PayrollRunLine` | Per-employee calculated result within a run: earnings/deductions/employer-contributions breakdown (structured JSON), gross pay, total deductions, total employer contributions, net pay | **Implemented** (Phase 2) - immutable once written; a reprocess deletes and recreates rows rather than mutating them |
| `PayrollException` | Recorded alongside or instead of a line for a data-quality/configuration gap (reason code, employee ref, resolution status) | **Implemented** (Phase 2; reason vocabulary and `status`/`resolved_by`/`resolved_at` extended in the Compensation Management revision - Section X) - the underlying business question (whether any reason should block the run) remains PENDING (Section X) |
| `PayrollAttendanceInput` | Payroll's own snapshot of one `attendance.finalized.v1` event (employee ref, work date, attendance ref) | **Implemented** (Phase 2) - new entity |
| `PayrollLeaveInput` | Payroll's own snapshot of one `leave.approved.v1` event (employee ref, leave request ref, approved units) | **Implemented** (Phase 2) - new entity; Leave's event carries no date range, so this snapshot is not period-filtered (a data-availability gap, not an invented assumption) |
| `Payslip` | One finalized, generated payslip per employee per run: employee ref, run ref, period ref, document ref, generated timestamp | **Implemented** (Phase 4) - unique per `(run_id, employee_ref)`; an adjustment run's payslip is an independent row, never a mutation of the original run's payslip |
| `PayslipGenerationFailure` | Recorded when payslip generation fails for one employee within a finalized run (technical retry-tracking entity, not in the original specification) | **Implemented** (Phase 4) - upserted per `(run_id, employee_ref)`; mirrors `PayrollException`'s precedent of surfacing a technical problem without blocking the run or inventing a business resolution |

## F. Database tables and important fields

Following the platform's established conventions (`UUID` primary keys, `TIMESTAMPTZ`
timestamps, `created_at/created_by/updated_at/updated_by/version` audit columns, `*_ref` for
cross-service references, `*_id` for local foreign keys):

- **payroll_periods**: `id`, `year`, `month`, `start_date`, `end_date`, `cut_off_date`, `status`.
- **employee_compensations**: `id`, `employee_ref`, `currency` (fixed `INR` initially, decision 2),
  `effective_from`, `effective_to` (nullable = still active), `status` (`ACTIVE`/`INACTIVE`,
  **added** in the Compensation Management revision - an administrative on/off switch independent
  of effective-dating).
- **compensation_components**: `id`, `compensation_id FK`, `component_code`, `component_type`
  (`EARNING`/`DEDUCTION`/`EMPLOYER_CONTRIBUTION`), `amount` (fixed value only — no formula/
  `calculation_type` column exists; **Implemented**), `proration_policy_code` (nullable;
  identifies the configurable proration policy for attendance/leave-sensitive components,
  Section H — no formula is stored here, only a policy identifier; **Implemented**),
  `calculation_strategy_code` (nullable; identifies the configurable calculation strategy for
  the component's amount, same pattern; **Implemented**, added during Phase 2).
- **pay_components**: `id`, `code`, `name`, `type`, plus (**added** in the Compensation Management
  revision) `active` and the standard `created_at/created_by/updated_at/updated_by/version` audit
  columns. **Implemented** — seeded with the Common India Payroll V1 Baseline's generic component
  names/types only (Section "Common India Payroll V1 Baseline" above); no rate, amount, or
  eligibility rule. The *actual* GDB catalogue (which of these are used, at what amount) remains
  PENDING_GDB_APPROVAL.
- **employee_statutory_profiles** (new, Compensation Management revision): `id`, `employee_ref`
  (unique), `pf_status`/`esi_status`/`pt_status` (`APPLICABLE`/`NOT_APPLICABLE`/
  `PENDING_VERIFICATION`), `pf_uan`, `pf_member_id`, `esi_identifier`, `pt_jurisdiction`
  (identifiers - sensitive, never logged), `pf_effective_from/to`, `esi_effective_from/to`,
  `pt_effective_from/to`, standard audit columns. **Implemented.** No statutory rate, threshold,
  or eligibility rule is stored - only applicability status and identifiers.
- **payroll_runs**: `id`, `period_id FK`, `run_type` (`REGULAR`/`ADJUSTMENT`), `corrects_run_id`
  (nullable, self-referencing FK, populated only for `ADJUSTMENT` runs), `status` (now including
  `PROCESSING`/`CALCULATION_FAILED`, Section D), `initiated_by`, `approved_by`, `approved_at`,
  `finalized_at`. **Implemented**, including `ADJUSTMENT` run creation (Section K): a new
  `ADJUSTMENT` run may only be created against a `FINALIZED` original (its own `periodId`,
  `corrects_run_id = original.id`); multiple adjustment runs may target the same original over
  time (no limit is imposed - Section K leaves that business question open), but only one
  adjustment per original may be *in flight* (not yet `FINALIZED`) at once, enforced by
  `existsByCorrectsRunIdAndStatusNot(correctsRunId, FINALIZED)` - a technical duplicate-submission
  guard, not an invented "one adjustment ever" accounting rule.
- **payroll_run_lines**: `id`, `run_id FK`, `employee_ref`, `gross_pay decimal(14,2)`,
  `total_deductions decimal(14,2)`, `total_employer_contributions decimal(14,2)` (added during
  Phase 2 - Section M lists employer contributions as a required payslip field, so this is
  aggregated alongside gross/deductions), `net_pay decimal(14,2)`, breakdown stored as structured
  JSON referencing `compensation_components` at calculation time (for reproducibility). No
  positivity constraint is applied to any amount column — adjustment-run lines may be negative
  (Section K). **Implemented** (Phase 2); unique per `(run_id, employee_ref)`.
- **payroll_exceptions** (new, Phase 2, not in the original specification): `id`, `run_id FK`,
  `employee_ref`, `reason` (`NO_EFFECTIVE_COMPENSATION`, plus - **added** in the Compensation
  Management revision - `MISSING_STATUTORY_PROFILE`, `MISSING_PF_IDENTIFIER`,
  `MISSING_ESI_IDENTIFIER`, `INVALID_EFFECTIVE_DATES`, `OVERLAPPING_COMPENSATION`,
  `INVALID_PAY_COMPONENT`, `OTHER_CONFIGURATION_ERROR`), `detected_at`, plus (**added** in the
  same revision) `status` (`OPEN`/`RESOLVED`), `resolved_at`, `resolved_by`. Unique per `(run_id,
  employee_ref, reason)` - **relaxed** from `(run_id, employee_ref)` in the same revision, since
  one employee may now have more than one distinct open exception reason in the same run.
  **Implemented.**
- **payroll_attendance_inputs** (new, Phase 2): `id`, `employee_ref`, `work_date`,
  `attendance_ref`, `source_event_id`, `received_at`. Unique per `(employee_ref, work_date)`.
  **Implemented.**
- **payroll_leave_inputs** (new, Phase 2): `id`, `employee_ref`, `leave_request_ref`,
  `approved_units`, `source_event_id`, `received_at`. Unique per `leave_request_ref`.
  **Implemented.**
- **payslips**: `id`, `employee_ref`, `run_id FK`, `period_id FK`, `document_ref`,
  `generated_at`. Unique per `(run_id, employee_ref)` - an adjustment run's payslip is a separate
  row referencing its own `run_id`, never an update to the original run's row. **Implemented**
  (Phase 4).
- **payslip_generation_failures** (new, Phase 4, not in the original specification): `id`,
  `run_id FK`, `employee_ref`, `failure_type`, `failure_message`, `occurred_at`. Upserted per
  `(run_id, employee_ref)` on retry, mirroring `payroll_exceptions`' precedent. **Implemented.**
- **outbox_events** / **processed_events**: identical shape to every other service in this
  platform (transactional outbox; inbox for the two consumed events in Section Q). **Implemented.**

No cross-service foreign keys are introduced; `employee_ref`, `document_ref` remain opaque
`*_ref` values per the platform-wide convention.

## G. Compensation model

Per decision 4, compensation is effective-dated: an `EmployeeCompensation` row has an
`effective_from` and optional `effective_to`, and a payroll run resolves "the compensation
effective for this employee during this period" rather than mutating a single current-salary
field. Multiple historical compensation records may exist per employee; the run must select
the one whose effective range covers the period being processed.

Per decision 1 (India baseline), Indian payroll structures typically separate compensation into
categories such as Basic Pay, House Rent Allowance, and other allowances on the earnings side,
and Provident Fund, Employee State Insurance, and Professional Tax on the statutory
deduction/contribution side, with Tax Deducted at Source computed separately. **These category
names are cited here only as illustrative examples of the shape a `PayComponent` catalogue
commonly takes in Indian payroll — not as GDB's actual catalogue, not as rates, and not as
eligibility rules.** The actual catalogue, its applicability, and every rate/threshold remain
PENDING_GDB_APPROVAL (Section X); no value is assumed.

### Compensation/pay-component/statutory-profile management (implemented)

HR/Finance management of this model — not just the calculation engine's read path — is now
implemented:

- **Compensation CRUD** — `EmployeeCompensation` + its `CompensationComponent` lines can be
  created, retrieved, listed per employee, and updated (full-replace of the component list,
  `effectiveFrom`/`effectiveTo`/`status` otherwise). Gated by `payroll.process` (writes) and
  `payroll.process`/`payroll.read.all` (reads) — the same permissions already documented in
  Section P, no new one. **There is no endpoint, under any permission, that lets an employee read
  or modify their own compensation** — self-service for this data is not offered at all, per this
  revision's explicit instruction, pending the HR-vs-Finance authority split (Section X).
- **Overlap prevention** — creating or updating a record so that its `[effectiveFrom,
  effectiveTo]` range overlaps another `ACTIVE` record for the same employee is rejected before
  persistence (409 `Conflict`). This keeps the calculation engine's pre-existing ambiguous-data
  failure mode (Section H) from being reachable through the new write path; it does not change
  what the engine does if ambiguous data somehow still exists.
- **Historical immutability** — a compensation record that a `FINALIZED` run has already read
  (determined by checking whether any of the employee's `PayrollRunLine`s belongs to a `FINALIZED`
  run whose period start date falls inside this record's effective range) can no longer be
  updated (409). No dedicated "used by a run" column was added; this is derived from existing
  `PayrollRunLine`/`PayrollRun`/`PayrollPeriod` data.
- **Pay-component catalogue CRUD** — code/name/type/active-status management for `PayComponent`,
  same permission split as above. A `CompensationComponent.componentCode` must reference an
  existing, `active` `PayComponent` whose `type` matches, or the request is rejected (422) — this
  reuses the already-seeded Common India Payroll V1 Baseline codes; no new catalogue content is
  introduced.
- **Employee statutory profile** — one `EmployeeStatutoryProfile` per employee, upserted (create
  or full-replace) and retrieved under the same permissions, with PF/EPF, ESI, and Professional
  Tax each tracked as applicability status + identifier + effective dates. **A missing identifier
  is never inferred as `NOT_APPLICABLE`** — the status field is the only source of truth for
  applicability; a missing identifier while status is `APPLICABLE` or `PENDING_VERIFICATION` is
  treated as a data-quality gap (see "Extended payroll exceptions" below), never silently assumed
  resolved by the absence of data.

### Extended payroll exceptions (implemented)

`PayrollException`'s reason vocabulary now also covers configuration/data-quality gaps beyond
"no effective compensation": `MISSING_STATUTORY_PROFILE`, `MISSING_PF_IDENTIFIER`,
`MISSING_ESI_IDENTIFIER`, `INVALID_EFFECTIVE_DATES`, `OVERLAPPING_COMPENSATION`,
`INVALID_PAY_COMPONENT`, `OTHER_CONFIGURATION_ERROR` (Section F). Every exception now carries a
resolution `status` (`OPEN`/`RESOLVED`) with `resolved_by`/`resolved_at`, exposed via `GET
/payroll/exceptions` (optionally filtered by run) and `POST /payroll/exceptions/{id}/resolve`.
Resolving an exception is pure record-keeping — it never re-runs calculation or changes a
`PayrollRunLine`. Of the new reasons, only `MISSING_STATUTORY_PROFILE`/`MISSING_PF_IDENTIFIER`/
`MISSING_ESI_IDENTIFIER`/`OTHER_CONFIGURATION_ERROR` (missing PT jurisdiction) are ever raised by
the calculation engine itself (Section H, additive check after a line is saved);
`OVERLAPPING_COMPENSATION`/`INVALID_EFFECTIVE_DATES`/`INVALID_PAY_COMPONENT` are enforced instead
at write time by the services above, so the states they name can no longer be persisted for the
engine to encounter. **Whether any exception reason should block a run remains exactly as
undecided as the pre-existing `NO_EFFECTIVE_COMPENSATION` question (Section X)** — this revision
adds visibility and resolution tracking only, never a blocking rule.

## H. Payroll calculation architecture

**Implemented** (Section Y Phase 2), as a configuration-driven pipeline with no statutory/tax
formula anywhere - see `com.growdigitalbridge.payroll.calculation` for the actual code. Per
decision 5, calculation happens inside Payroll Service, not an external provider. The
calculation pipeline for a `PayrollRun`:

1. Resolve the `PayrollPeriod` being processed. **Implemented.**
2. Resolve the set of employees included in the run (Section I). **Implemented** (Phase 1 snapshot).
3. For each included employee, resolve the `EmployeeCompensation` effective for that period.
   **Implemented** (`CompensationResolver`); an employee with none produces a `PayrollException`
   instead (Section E/X), never a blocked run. After a line is successfully saved, the employee's
   `EmployeeStatutoryProfile` is additionally checked (Compensation Management revision, Section
   G) and a `MISSING_STATUTORY_PROFILE`/`MISSING_PF_IDENTIFIER`/`MISSING_ESI_IDENTIFIER`/
   `OTHER_CONFIGURATION_ERROR` exception is recorded per gap found — purely additive, never
   replacing or blocking the line.
4. Incorporate attendance/leave inputs (Section I) through a **configurable proration policy**
   (below) to determine any pay adjustment (e.g. for unpaid leave or loss-of-pay days).
   **Implemented** as a registry lookup; the only registered policy is the no-op default below.
5. Apply each `CompensationComponent` (earnings, deductions, employer contributions) through a
   **configurable calculation strategy** (`ComponentCalculationStrategy`, with `StatutoryCalculator`/
   `TaxCalculator` as distinct, separately-registrable extension points for statutory/tax-coded
   components specifically). **Implemented**; the only registered strategy
   (`FixedAmountStrategy`) returns the component's own configured amount unchanged - the pending
   catalogue content and pending deduction/statutory rules (Section X) are not implemented.
6. Compute `gross_pay`, `total_deductions`, `total_employer_contributions`, `net_pay` per
   employee, persisted as an immutable `PayrollRunLine` (structured JSON breakdown). **Implemented.**
7. On finalization only (Section J), generate one `Payslip` + PDF per `PayrollRunLine`, upload it
   through Document Service's workload-upload contract (Section N/U), and publish
   `payslip.generated.v1` per payslip (Section Q). **Implemented** (Phase 4) - this step runs
   identically for a `REGULAR` or an `ADJUSTMENT` run (nothing branches on `run_type`), so a
   finalized adjustment run gets its own independent `Payslip`/PDF/event, never a mutation of the
   original run's payslip. A per-employee failure is recorded in `PayslipGenerationFailure`
   without blocking other employees or re-opening the now-`FINALIZED` run; re-invoking
   `POST /payroll/runs/{id}/finalize` on an already-finalized run safely retries only the
   still-missing payslips (no re-mutation, no duplicate event).

Reprocessing (before `FINALIZED`) is idempotent: existing lines/exceptions for the run are
deleted and recreated, never duplicated, enforced by a database uniqueness constraint on
`(run_id, employee_ref)` for both tables. A genuine calculation failure (e.g. corrupted/ambiguous
compensation data) rolls back every write from that attempt - no partial `PayrollRunLine` is ever
left behind - and the run is safely marked `CALCULATION_FAILED` (Section D) in a separate,
already-committed step, from which it can be reprocessed like any other reprocessable state.

### Configurable proration policy (technical model only)

The calculation pipeline must not hard-code any unpaid-leave/loss-of-pay formula. Step 4 is
implemented as a pluggable **proration policy** the run resolves per compensation component,
not a fixed calculation baked into the pipeline:

- A `ProrationPolicy` is a named, versioned strategy identifier, stored as `proration_policy_code`
  on `CompensationComponent`, that the calculation pipeline looks up (via `ProrationPolicyRegistry`)
  and invokes for `EARNING`-type components. It receives the employee's resolved compensation, the
  relevant `PayrollAttendanceInput`/`PayrollLeaveInput` snapshot rows for the employee, and the
  period's calendar bounds, and returns an adjustment amount. **Implemented.**
- **Default policy**: until GDB supplies a real formula, the only non-invented default is a
  **no-op policy** — full compensation is paid regardless of attendance/leave state. This is
  the sole default this document specifies, because paying full compensation is the absence of
  a rule, not the assertion of one; any other default (e.g. per-day deduction) would be
  inventing the exact business formula the task requires this document not to invent.
  <br>An actual formula (e.g. `unpaid_days × (monthly_rate / days_in_period)`) may only be
  configured once GDB Finance/Legal supplies it — see Section X. **`NoOpProrationPolicy` is
  implemented and is the only registered policy; no other policy exists in code.**
- The policy identifier is data-driven configuration, not a code branch per employee, so a real
  formula can be introduced later without changing `PayrollRun`/`PayrollRunLine`'s schema or
  the calculation pipeline's control flow — only the resolved policy implementation changes.
- This mirrors the platform's existing precedent for leaving a business rule unimplemented
  rather than invented: Workflow Service's `rulesJson` is stored opaque and never interpreted
  by the service until a real approval-resolution policy is supplied (`docs/workflows/WORKFLOWS.md`).

Provider selection (decision 5 confirms none is used for calculation) does not preclude a
future payment/disbursement provider integration (Section X, "payroll/payment provider" —
still pending, and scoped to payment execution, not calculation).

## I. Attendance/Leave input model

**Implemented** (Section Y Phase 2). Per decision 6, a payroll run's inputs are:
- **Finalized attendance** — Attendance Service's `AttendanceRecord` in status `FINALIZED` only;
  non-finalized records must never be read.
- **Approved leave** — Leave Service's `LeaveRequest` in status `APPROVED` only.

Consistent with the platform's dominant integration pattern (asynchronous domain events over
synchronous cross-service fetches for non-immediate needs), Payroll consumes these
asynchronously: `attendance.finalized.v1` and `leave.approved.v1`, both already named as
deferred (`Payroll*`) consumers in `COMMUNICATION.md`'s event table. This resolves
`WORKFLOWS.md`'s previously either/or wording ("consumes... events or fetches... through
explicit APIs") in favor of the pattern every other consuming service in this platform already
uses, and requires no new business decision — it is a technical integration choice, not a
statutory or compensation rule. Both consumers use the same inbox/`ProcessedEvent` idempotency
pattern already established by Document, Asset, and Workflow Service, persisting only the exact
fields each event carries into `PayrollAttendanceInput`/`PayrollLeaveInput` (Section F) - never a
fetch back to Attendance/Leave Service. Attendance's event is additionally re-checked for
`status = FINALIZED` before being persisted (defense in depth); Leave's event carries no status
field at all since `leave.approved.v1` is only ever published on approval, and it carries no
date range, so leave inputs are not yet filterable to a specific payroll period.

### Employee scope for a run (technical ambiguity resolved in this revision)

The original specification said "for each active employee" (Section H) without stating where
that list comes from. Resolved here: at run-initiation time, Payroll Service synchronously
calls Employee Service for the current active-employee list, matching the bounded, one-hop
"Employee lookup while creating a domain record" pattern `docs/architecture/COMMUNICATION.md`
already documents and every other service's `EmployeeClient` already implements. The resolved
list is snapshotted onto the `PayrollRun` at `DRAFT` creation (not re-queried mid-run), so a
run's employee membership is stable and reproducible even if Employee Service data changes
while the run is in progress. An employee with no `EmployeeCompensation` effective for the
period is included in the snapshot but produces no `PayrollRunLine` — whether that is an error
that blocks the run or a silently-skipped employee is a business process question, not resolved
here, and is added to Section X.

## J. Approval and finalization flow

Per decision 7, `PENDING_APPROVAL → APPROVED` requires an explicit decision before
`APPROVED → FINALIZED` can occur.

### Maker-checker permission split (resolved in this revision)

`RBAC.md` currently documents only `payroll.process`, with no distinct approval permission —
insufficient for genuine segregation of duties, since one permission cannot distinguish "the
person who prepared this run" from "the person authorized to approve it." This revision
introduces a second, distinct permission:

- **`payroll.process`** — create/calculate/process: `POST /payroll/runs` (initiate a regular
  run) and `POST /payroll/runs/{id}/adjustments` (initiate an adjustment run, Section K). This
  is the *maker* side.
- **`payroll.approve`** — approve/reject/finalize: `POST /payroll/runs/{id}/approve`,
  `POST /payroll/runs/{id}/reject`, and `POST /payroll/runs/{id}/finalize` (Section D). This is
  the *checker* side. Reject is gated by the same permission as approve because both are
  outcomes of the single approval decision, mirroring how Leave Service's own decision endpoint
  uses one permission tier for both outcomes.

**`payroll.approve` is now defined in `RBAC.md`'s permission catalogue** (added by the
"source-of-truth documentation synchronization" revision below) — the same `<domain>.<verb>`
naming shape every other permission in `RBAC.md` already follows (e.g. `policy.publish`,
`asset.assign`), not a new capability invented from nothing.

**Self-approval prevention (maker-checker enforcement):** holding `payroll.approve` is
necessary but not sufficient. The service layer must additionally reject an approval/finalize
attempt where the acting identity equals the run's recorded `initiated_by` actor, *regardless*
of whether that identity also holds `payroll.approve` — i.e. a single person holding both
permissions still cannot approve their own initiated run. This mirrors Workflow Service's own
existing rule that only the original assignee (never an arbitrary holder of the decide
permission) may act on a specific task, and Asset Service's rule that a plain permission grant
is not by itself sufficient authorization for a specific resource.

**Admin is not broadened.** Per `RBAC.md`'s already-stated rule ("Admin | Identity/role/
operational configuration permissions; no automatic HR or payroll data access"), Admin receives
neither `payroll.process` nor `payroll.approve` automatically by virtue of this change. Both
remain explicitly-granted permissions, consistent with every other sensitive permission in this
platform.

Rejection returns the run to `DRAFT` for correction and re-calculation; it does not delete the
run or its history (audit requirement, Section R).

## K. Correction/reprocessing flow

Per decisions 8–9, once `FINALIZED` a run is never edited. A correction is a new `PayrollRun`
with `run_type = ADJUSTMENT` and `corrects_run_id` pointing at the original finalized run. An
adjustment run goes through the identical lifecycle (Section D) including its own approval step
(gated by `payroll.process`/`payroll.approve` exactly like a regular run, Section J), and
produces its own `PayrollRunLine`/`Payslip` records — it never mutates the original run's rows.

**Status: implemented.** `POST /payroll/runs/{id}/adjustments` (`payroll.process`) creates the
adjustment run:

- **Original-run validation** — the path's `{id}` must resolve to an existing `PayrollRun` whose
  status is exactly `FINALIZED`; any other status is rejected (`422`). The original run is only
  ever *read* (its `id`/`period_id`) — no field on it is ever written, verified by a test
  asserting the original row is byte-for-byte unchanged (status, `approved_by`, `finalized_at`)
  after an adjustment is created, processed, and finalized against it.
- **New run** — `run_type = ADJUSTMENT`, `corrects_run_id = <original.id>`, `period_id =
  <original.period_id>` (an adjustment corrects that period's payroll, it does not open a new
  one), starting at `DRAFT` with the same technical employee-snapshot mechanism as a regular run
  (`EmployeeClient.resolveActiveEmployeeRefs()` - no new eligibility rule is invented for which
  employees an adjustment covers).
- **Identical downstream pipeline** — from `DRAFT` onward an adjustment run is processed,
  approved, and finalized through the *exact same, unmodified* code as a regular run: the same
  `PayrollCalculationEngine` (Section H), the same maker-checker/self-approval-prevention rule
  (Section J - the identity that created the adjustment may not approve/reject/finalize it,
  exactly as for a regular run), and the same finalize-time payslip generation (Section H step 7).
  Nothing in that pipeline branches on `run_type`.
- **Idempotency** — repeated adjustment creation does not create unintended duplicates. Section
  K's own business question ("any limit on how many adjustment runs may reference the same
  original") remains open, so the guard implemented is narrower than a hard one-per-original
  limit: a new adjustment is rejected (`409`) while an earlier adjustment against the *same*
  original has not yet reached `FINALIZED`; once one reaches `FINALIZED`, a later, genuinely new
  adjustment against the same original may be created freely. This mirrors the spirit of the
  regular-run uniqueness check (Section T) without inventing a business rule nobody has decided.
- **Payslip** — on finalization, the adjustment run generates its own `Payslip` (its own
  `document_ref`, its own PDF, uploaded through the same Document Service contract) and publishes
  its own `payslip.generated.v1` for it - an independent record, connected to the original only by
  `corrects_run_id` on the run itself. The original run's own `Payslip` row is never touched.

### Signed adjustment lines (technical model)

To represent both additional pay and clawback without inventing an accounting policy, an
adjustment run's `PayrollRunLine` amounts (`gross_pay`, `total_deductions`, `net_pay`, and each
component amount within the JSON breakdown) are permitted to be **negative** — the columns are
already `decimal(14,2)` (Section F) with no positivity constraint added. A negative line
represents a clawback/reduction against the original run's result; a positive line represents
an additional payment. This is a structural allowance only, not a computation rule: the
specification does not define how an adjustment line's sign or magnitude is derived from the
error being corrected — that derivation is a business/accounting decision.

**Explicitly PENDING_GDB_APPROVAL** (unchanged from the original specification, restated
precisely per this task's instruction not to invent accounting policy): whether an adjustment
run's totals net against the original run's totals for reporting/payslip purposes, whether a
single adjustment run may correct multiple original runs, and any limit on how many adjustment
runs may reference the same original run. Until GDB supplies this, an adjustment run's
`PayrollRunLine`/`Payslip` are additional records alongside the original's, connected only by
`corrects_run_id` — no automatic netting or display consolidation is implemented.

**Known limitation: correcting historical compensation.** Compensation resolution (Section G) is
date-based — `CompensationResolver` selects the `EmployeeCompensation` effective as of the
*period's own start date* — and an adjustment run always shares its original's `period_id`
(above). This means that if nothing about an employee's compensation records has changed, an
adjustment run's calculation reproduces the *same* figures as the original, since it resolves the
identical compensation as of the identical date; the engine does not infer what "should" have
been different. Producing an actually-different, corrected figure requires the underlying
`EmployeeCompensation`/`CompensationComponent` data itself to be corrected first - and because the
calculation engine already treats two compensation records whose effective ranges overlap for the
same employee/date as an ambiguous, hard-failing state (Section H, "calculation failure/
rollback"), simply adding a second, overlapping "correction" record is not a safe way to do this
today. A true retroactive compensation correction mechanism (e.g. superseding or end-dating a
historical record without creating an overlap) is not implemented and is not part of this phase's
scope - it is a data-correction capability, not an adjustment-run concern, and remains open.

## L. Payslip functional requirements

**Employee-facing:**
- My Payslips
- Payslip History
- View Payslip
- Download PDF
- Print (client-side, using the downloaded PDF — no separate API)
- Pay period filtering
- Financial year filtering

**HR/Finance-facing:**
- View/download any employee's payslip for an authorized purpose, subject to enhanced audit
  logging (Section R).

All of the above are self-only for employees (decision 10); there is no team-scoped payslip
tier, matching `RBAC.md`'s existing statement that "Employees can access only their own
payslips."

## M. Payslip PDF content specification

Per decisions 11–12, every payslip must contain:

- GDB branding (logo/company name — actual brand assets are a design/asset task, not a data
  requirement, and are out of scope for this document).
- Employee identity (name, employee number).
- Department/designation, where authorized to display.
- Joining date, where authorized to display.
- Payment date.
- Payroll period (the `PayrollPeriod` this payslip covers).
- Earnings breakdown (one line per `EARNING`-type component from the run's `PayrollRunLine`).
- Deductions breakdown (one line per `DEDUCTION`-type component).
- Employer contributions (one line per `EMPLOYER_CONTRIBUTION`-type component; shown for
  transparency, not included in the employee's net pay calculation).
- Gross pay.
- Total deductions.
- Net pay.
- Amount in words (net pay rendered as English words, a standard Indian payslip convention —
  a formatting/presentation requirement, not a statutory rule, so it is included here rather
  than deferred).
- Year-to-date (YTD) totals per earning/deduction category for the current financial year.
- Applicable tax information (the TDS amount deducted for the period, and YTD tax deducted) —
  the *value* is computed per PENDING statutory rules (Section X); the payslip's *field* for
  displaying it is locked by decision 12.
- Document reference (the `documentRef` pointing to the stored PDF in Document Service).
- Generated timestamp.

The India-common category names used above (e.g. "employer contributions," "TDS") describe the
*shape* of the payslip only; no specific rate, threshold, or eligibility rule is asserted.

## N. Payslip storage architecture

Per decision 13, Payroll does not introduce its own object storage — the generated PDF is
stored the same way Document Service already stores files (per `DATABASE.md`: "Document
binaries are stored outside PostgreSQL in private object storage; Document stores object keys,
integrity metadata, classification, and authorization records"). `Payslip.document_ref` is an
opaque cross-service reference to that Document Service record, exactly like Expense Service's
`ReceiptReference.document_ref`.

### Service-to-service contract (resolved in this revision)

Every existing cross-service call pattern in this platform (`EmployeeClient`/`OrganizationClient`
in every prior service) is a *caller-token relay*: it forwards a live user's own bearer token to
answer that same user's request. Document Service's own documented upload endpoint
(`POST /documents/uploads`, gated by `document.upload.self`) is likewise a self-service action
scoped to the uploading caller. Payroll's payslip generation is a *batch workload acting on
behalf of many other employees at once*, which no relay-based call can express — the workload
has no user token to relay, and even if it did, relaying an arbitrary employee's token would be
a security violation, not a fix.

This is **not**, however, an unprecedented gap in the platform's security architecture:
`docs/security/SECURITY.md` already states "Internal calls use workload identity or OAuth2
client credentials; user context is propagated only when required for authorization and audit"
and "Internal workload calls use narrowly scoped OAuth client credentials/workload identity."
The authentication *mechanism* is therefore already documented; what is missing is the
*document-service-side API contract* to accept such a call. The contract below uses only that
already-documented mechanism and Document Service's own already-documented entity/lifecycle —
it does not invent a new security model.

**Workload/service identity.** Payroll Service authenticates to Document Service using an
OAuth2 client-credentials-issued JWT for a dedicated `payroll-service` client (an instance of
the `Client` entity `docs/database/DATABASE.md` already documents under Auth), not a relayed
user token. Document Service's resource-server validation (issuer/audience/signature/expiry)
is unchanged; the token's subject identifies the workload, not an employee.

**Required new Document Service capability (a required cross-service contract change, not
implemented by this document or by Payroll):**

| Aspect | Contract |
|---|---|
| Endpoint (illustrative name, TBD at implementation) | `POST /documents/workload-uploads` |
| Caller identity | Workload/client-credentials token for an explicitly authorized service client (e.g. `payroll-service`) — never a relayed employee token |
| Target employee reference | Request body carries an explicit `ownerRef` (the employee the document belongs to) — the one field a self-service upload never needs, since today `ownerRef` is always the caller's own resolved identity |
| Document ownership | The created `Document.owner_ref` is set to the request's `ownerRef`, exactly as a self-service upload sets it to the caller — no new ownership model, just a workload-supplied value instead of a self-resolved one |
| Classification | Populated using Document Service's existing freeform `classification` field (`docs/database/DATABASE.md`) — no new classification vocabulary is introduced by this contract; Document Service's own classification-vocabulary gap remains separately open |
| Object creation | Identical two-phase flow Document Service already implements: create returns an object key, a subsequent complete call confirms upload — no new upload mechanic |
| Checksum/MIME/size | Identical fields Document's `DocumentVersion` already has (`checksum`, `mimeType`, `sizeBytes`) — no new fields |
| Scan/quarantine lifecycle | Reuses Document's existing `PENDING_SCAN → AVAILABLE/QUARANTINED` checksum-match gate unchanged — no new scan mechanism |
| Secure download | **No new endpoint needed.** Once `ownerRef` is set correctly at creation, the existing `GET /documents/{id}/download` (gated by `document.read.self/team/all`) already serves the employee correctly, because that check already compares `ownerRef` against the caller's resolved self — the gap is only on the creation side |
| Audit | Uses Document Service's existing audit mechanism; the recorded actor is the workload identity (e.g. `payroll-service`), distinguishable from a person, exactly as any JWT-subject-derived actor already is today |

**Required authorization concept:** the `payroll-service` client must be granted a distinct,
narrowly-scoped authorization to call this endpoint — a client/workload-level grant (per
`SECURITY.md`'s "narrowly scoped OAuth client credentials"), not a `document.*` **user**
permission, since no human is acting. **Implemented** as the `workload.document.upload`
authority, gating `POST /documents/workload-uploads` and (alongside `document.upload.self`/
`document.manage`) `POST /documents/uploads/{id}/complete`.

**Status: implemented.** `POST /documents/workload-uploads` now exists in Document Service,
documented in `docs/api/API.md`'s Documents section and cross-referenced from
`docs/architecture/MICROSERVICES.md`'s Document and Payroll rows. It creates the document in
`PENDING_SCAN`; the same authorized workload identity then completes it through the existing
`POST /documents/uploads/{id}/complete` (extended to also accept `workload.document.upload`,
authorized by comparing the acting identity to the document's own recorded creator) — reusing
the unchanged checksum-match scan/quarantine gate, with no second upload or scan mechanism.
Idempotent per `(ownerRef, checksum)`: a duplicate submission is rejected with `409 Conflict`.
Payroll's own Phase 4 (Section Y) now integrates against this endpoint: `PayslipGenerationService`
calls `createWorkloadUpload` then uploads the real PDF bytes through the content endpoint, then
`completeUpload`, for both regular and adjustment runs alike.

## O. APIs

The four contracts already documented in `API.md` remain the baseline:

- `GET /payroll/payslips/me` — self payslip list; `payslip.read.self`.
- `GET /payroll/payslips/{id}` — self or authorized payslip detail; `payslip.read.self/all`.
- `GET /payroll/runs` — run list/status; `payroll.read.all`.
- `POST /payroll/runs` — start a run for a period; `payroll.process`.

The following are flagged **minimum necessary additions** required to make decisions 7, 9, and
12 actually reachable. Most use only existing documented permissions; the approve/reject/
finalize group uses the new `payroll.approve` permission introduced in Section J/P, which is
itself a required `RBAC.md` addition (Section X) — not an existing permission:

- `POST /payroll/runs/{id}/approve` — `payroll.approve` (Section J).
- `POST /payroll/runs/{id}/reject` — `payroll.approve` (Section J).
- `POST /payroll/runs/{id}/finalize` — `payroll.approve` (Section D/J).
- `POST /payroll/runs/{id}/adjustments` — `payroll.process` (Section K).
- `GET /payroll/payslips/{id}/download` — `payslip.read.self/all` — required by decision 12's
  "secure download"; returns a short-lived, authenticated download reference, never a public URL
  (Section S).

**Added in the Compensation Management revision** (same reuse-only-existing-permissions
principle — no new permission introduced):

- `POST /payroll/compensations` / `PATCH /payroll/compensations/{id}` — `payroll.process`.
- `GET /payroll/compensations` / `GET /payroll/compensations/{id}` — `payroll.process` or
  `payroll.read.all`.
- `POST /payroll/pay-components` / `PATCH /payroll/pay-components/{id}` — `payroll.process`.
- `GET /payroll/pay-components` / `GET /payroll/pay-components/{id}` — `payroll.process` or
  `payroll.read.all`.
- `PUT /payroll/statutory-profiles/{employeeRef}` — `payroll.process` (create-or-replace).
- `GET /payroll/statutory-profiles/{employeeRef}` — `payroll.process` or `payroll.read.all`.
- `GET /payroll/exceptions` — `payroll.process` or `payroll.read.all`.
- `POST /payroll/exceptions/{id}/resolve` — `payroll.process`.

None of these endpoints accept a client-supplied employee identity as a self-service path — every
one requires a payroll-authorized caller, matching the explicit "no employee self-service for
this data" instruction.

No endpoint beyond this list is proposed. Exact request/response shapes are left to
implementation time and are not part of this decision-locking exercise.

## P. RBAC permissions

| Permission | Status | Gates |
|---|---|---|
| `payroll.read.self/all` | Already documented (`RBAC.md`) | `GET /payroll/runs` (`.all`) |
| `payroll.process` | Already documented | `POST /payroll/runs`, `POST /payroll/runs/{id}/adjustments` — create/calculate/process (maker side) |
| `payroll.approve` | **New — added to `RBAC.md`'s permission catalogue and role-mapping notes** | `POST /payroll/runs/{id}/approve`, `/reject`, `/finalize` — approve/finalize (checker side) |
| `payslip.read.self/all` | Already documented | `GET /payroll/payslips/me`, `GET /payroll/payslips/{id}`, `GET /payroll/payslips/{id}/download` |

`payroll.approve` is the only new permission this specification introduces, and it is
introduced deliberately per this task's explicit instruction — it is not an unplanned addition.
Its naming follows the existing `<domain>.<verb>` convention exactly. Section J documents the
maker-checker split and self-approval prevention this permission exists to support. Admin does
not receive either payroll permission automatically (Section J).

The Compensation Management revision's endpoints (Section O) deliberately introduce **no new
permission** — they reuse `payroll.process`/`payroll.read.all` exactly as already documented
above, since the exact HR-vs-Finance authority split remains PENDING_GDB_APPROVAL (Section X) and
inventing a split-specific permission now would prejudge that decision.

## Q. Domain events

**Produces:**
- `payroll.processed.v1` (`PAYROLL_PROCESSED`) — already documented in `COMMUNICATION.md`:
  payroll run/period ID, employee count. Emitted once per `FINALIZED` run (regular or
  adjustment), per decision 16's versioned-envelope requirement (`eventVersion`, `correlationId`,
  etc. — the same envelope every other service already uses). **Unchanged by this revision.**
- `payslip.generated.v1` (`PAYSLIP_GENERATED`) — **new, introduced in this revision** to resolve
  the "payslip-ready" gap. Per-payslip granularity, because `PAYROLL_PROCESSED` is aggregate-only
  and cannot tell a downstream consumer *which* employees now have a payslip.
  - **Payload** (IDs and minimal non-sensitive context only, per `COMMUNICATION.md`'s own rule
    that "Payloads contain only minimal references/context—never tokens or payroll values"):
    `payslipId`, `employeeId`, `runId`, `periodId`, `generatedAt`. **No amount, no gross/net
    pay, no tax value** — consistent with the same rule already governing every event in this
    platform.
  - **Producer:** Payroll Service, emitted once per `Payslip` row created during finalization
    (Section H, step 7), via the same transactional outbox every service already uses.
  - **Idempotency:** guaranteed by `Payslip`'s own `(run_id, employee_ref)` uniqueness
    (Section T) — at most one `Payslip` row, and therefore at most one outbox row/event, is ever
    produced per employee per run. Consumers apply the platform's standard inbox/
    `ProcessedEvent` pattern for their own duplicate-delivery safety.
  - **Possible consumers:** Notification (the primary motivating use case — notify the employee
    their payslip is available), Audit (per the platform default of auditing all domain events),
    and Reporting once approved (per its generic "consumes approved events" pattern). No
    consumer is mandated by this document; each remains free to integrate or not.
  - **Naming rationale:** `PAYSLIP_GENERATED` (past-tense verb) was chosen over `PAYSLIP_READY`
    (adjective) for consistency with every existing event name in `COMMUNICATION.md`
    (`DOCUMENT_UPLOADED`, `ASSET_ASSIGNED`, `EXPENSE_SUBMITTED`, `WORKFLOW_COMPLETED`) — a
    naming-convention choice, not a business decision.
  - Added to `COMMUNICATION.md`'s event contract table.

**Consumes:**
- `attendance.finalized.v1` — per decision 6 and `COMMUNICATION.md`'s deferred `Payroll*`
  consumer flag.
- `leave.approved.v1` — per decision 6 and the same deferred consumer flag.
- Payroll does **not** consume `employee.deactivated.v1` — it is not in that event's documented
  consumer list (`Identity*, Asset, Document, Workflow, Audit, Reporting*`), so no such
  integration is assumed.

## R. Audit requirements

Per decision 14 and the existing platform baseline (`DATABASE.md`: "All values/access/exports
highly sensitive and audited"; `MICROSERVICES.md`: "Separate routes/credentials/logging; fail
closed"):
- Every state transition in Section D (initiate, calculate, submit for approval, approve,
  reject, finalize, adjustment-initiate) is an audited action with actor, timestamp, and
  correlation ID — matching Workflow Service's existing "every transition immutable/audited"
  standard.
- Every payslip view, download, and export (by anyone, including the employee themself) is
  logged, per `RBAC.md`'s existing rule that "sensitive document downloads require enhanced
  audit logging" — applied here to payslips specifically per decision 14.
- HR/Finance access to any employee's payslip is logged distinctly from the employee's own
  self-access, so an access pattern audit can distinguish "viewed own payslip" from
  "HR/Finance viewed employee X's payslip."

## S. Security requirements

- Deny-by-default JWT resource server, identical posture to every service in this platform: no
  `JwtDecoder` bean until GDB supplies an OIDC issuer.
- Self-only employee access is enforced server-side from the validated token subject via the
  Employee Service self-resolution pattern already used everywhere — never a client-supplied
  employee identifier.
- No public URLs for any payslip document; `GET /payroll/payslips/{id}/download` issues a
  short-lived, authenticated reference only (Section O), never a permanently-guessable link.
- Field masking applies to payroll amounts and payslip content in any non-payslip-view context
  (e.g. a future report), per `RBAC.md`'s existing "Field masking... apply to... payroll...
  and exports" rule.
- Separate audit trail entries for self-access versus HR/Finance access (Section R).

## T. Idempotency requirements

Per decision 15:
- A `PayrollRun` is uniquely identified by `(period_id, run_type, corrects_run_id)` — a
  duplicate "start run" request for the same period/type is rejected as a conflict, not
  silently duplicated (mirroring Attendance's own "natural idempotency guard" precedent for
  check-ins).
- `PayrollRunLine`/`Payslip` generation is idempotent per `(run_id, employee_ref)` — reprocessing
  the same run never produces duplicate lines or payslips.
- Outbox publication and event consumption use the platform's existing transactional
  outbox/inbox pattern, which is already idempotent and duplicate-safe by construction.

## U. Document Service integration

See Section N for the storage architecture and the flagged cross-service capability gap. At a
technical level, the generated PDF bytes are handed to Document Service's existing storage
mechanism and Payroll stores only the resulting `document_ref` — Payroll never stores payslip
binaries itself, matching decision 13 and the platform-wide "no binary files in the database"
rule already stated for Document Service.

## V. Notification integration

Following the existing pattern (`COMMUNICATION.md`: `PAYROLL_PROCESSED` consumers include
Notification), Notification is informed at the aggregate level when a run finalizes via
`PAYROLL_PROCESSED` (run/period ID, employee count). For per-employee "your payslip is
available" notifications, Notification instead consumes the new `payslip.generated.v1`
(Section Q), which carries the specific `employeeId`/`payslipId` `PAYROLL_PROCESSED` cannot.
`PAYROLL_PROCESSED` is retained unchanged for aggregate/administrative notification and audit
purposes; the two events serve different granularities and neither replaces the other.

## W. Reporting integration boundary

Payroll may eventually publish data Reporting consumes, following Reporting's existing generic
"consumes approved events" pattern (`MICROSERVICES.md`). Per `docs/PAYROLL_REPORTING_DECISIONS.md`
Section 1 Q23 and Section 2 Q15, the exact payroll metrics/reports Reporting must support are
explicitly **PENDING_GDB_APPROVAL** and are not defined here. Payroll's implementation must not
assume any specific report exists.

## X. Business decisions still pending

### Business decisions pending GDB/Finance/Legal approval (unchanged by this revision)

Carried forward from `docs/PAYROLL_REPORTING_DECISIONS.md`, restated as
**PENDING_GDB_APPROVAL** exactly as instructed — no value invented for any of them:

- Actual salary structure.
- Actual pay component catalogue.
- Deduction policies.
- Benefits.
- Statutory applicability/rules.
- TDS rules/configuration supplied by Finance/Legal.
- Payroll/payment provider.
- Exact HR vs Finance authority split.
- Retention period.
- Exact payroll reporting requirements.
- The actual attendance/leave-to-pay proration formula (Section H defines the pluggable
  interface; the formula itself is not defined here).
- The accounting/netting treatment for adjustment runs (Section K defines the signed-line
  technical model; the policy itself is not defined here).
- Whether an employee with no effective compensation for a period blocks the run or is silently
  skipped (Section I).

### Cross-document contract changes (all resolved)

- **`docs/security/RBAC.md`** — `payroll.approve` added to the permission catalogue, the
  maker-checker rule added to the Rules section, and the HR/Finance split explicitly marked
  PENDING_GDB_APPROVAL (Section J/P). **Done.**
- **`docs/architecture/COMMUNICATION.md`** — `payslip.generated.v1` (`PAYSLIP_GENERATED`) added
  to the event contract table with payload/versioning/idempotency notes (Section Q). **Done.**
- **`docs/api/API.md`** (Documents section) and **`docs/architecture/MICROSERVICES.md`**
  (Document and Payroll rows) — the workload-upload contract documented, and **`POST
  /documents/workload-uploads` is now implemented and tested in Document Service** (Section
  N/U). **Done.**

No further cross-document or cross-service prerequisite blocks Payroll's own implementation
phases (Section Y) beyond the business decisions above. Payroll Service Phases 1–4 (Foundation,
Calculation Core, Approval/Finalization, Payslip Generation) and Adjustment Runs (Section K) are
all implemented at the technical level. Real payroll calculation values and every remaining
sensitive business function are still gated pending GDB approvals.

## Y. Implementation phases

1. **Foundation** — module scaffolding, security baseline, `PayrollPeriod`/`EmployeeCompensation`/
   `CompensationComponent`/`PayComponent` schema and CRUD needed only for internal setup (no
   catalogue content — that's pending).
2. **Calculation core** — `PayrollRun`/`PayrollRunLine` lifecycle (Section D), attendance/leave
   event consumption (Section I), calculation pipeline (Section H). **Implemented** (see
   "Revision: Payroll Phase 2 (Calculation Core) implemented" above) at the *technical* level -
   configuration-driven, no formula. Producing a *real* payroll number still requires the pending
   proration formula and pay component catalogue *content* to be supplied (Section X); this phase
   does not wait for that to exist in code, only for those values to become meaningful.
3. **Approval/finalization** — Section J/K endpoints and state transitions, including the
   `payroll.approve` permission and self-approval prevention. **Implemented** - `/approve`,
   `/reject`, `/finalize`, and `POST /payroll/runs/{id}/adjustments` (Section K, creating an
   `ADJUSTMENT` run against a `FINALIZED` original) all exist and are exercised by
   `PayrollIntegrationTest`/`PayrollAdjustmentIntegrationTest`.
4. **Payslip generation** — PDF content (Section M), Document Service integration (Section N/U)
   — and self-service access (Section B). **Implemented** - `PayslipGenerationService` generates
   one `Payslip`/PDF per `PayrollRunLine` on finalization (for both regular and adjustment runs),
   uploads it through Document Service's workload-upload contract, and `GET /payroll/payslips/me`,
   `/{id}`, `/{id}/download` serve it back self-only (or `payslip.read.all`), per
   `PayslipIntegrationTest`.
5. **Messaging** — `payroll.processed.v1` and `payslip.generated.v1` producers,
   `attendance.finalized.v1`/`leave.approved.v1` consumers (Section Q), platform wiring (gateway
   route, database init, per the established per-service pattern; next available port per
   existing convention). **Implemented** - both producers and both consumers are live against a
   real broker, verified by `PayrollMessagingIntegrationTest`/`PayslipIntegrationTest`.
6. **Audit/security hardening** — Section R/S controls, verified before any real payroll data
   is processed. Every state transition (including adjustment creation) is audit-logged
   (`PayrollAuditLog`); the real-data hardening pass itself remains pending real payroll content.
7. **Compensation management** — HR/Finance CRUD for `EmployeeCompensation`/
   `CompensationComponent`/`PayComponent`, the new `EmployeeStatutoryProfile` entity, and the
   extended `PayrollException` reason/status model (Section G/E/O). **Implemented** — see the
   "Revision: Employee Compensation Management, Statutory Profile, and extended Payroll
   Exceptions implemented" section above. Still gated on Section X: no real salary value, pay-
   component catalogue content, statutory rate/threshold/eligibility rule, or HR-vs-Finance
   authority split exists.

**Revised finding:** the original wording above said Phase 1 could not begin coding at all until
the pay-component catalogue and statutory rule source (Section X) were approved. That was too
broad. Section F already fully specifies the *shape* `CompensationComponent`/`PayComponent` take
(component code, type, amount, proration-policy code) independent of the catalogue's *content*
(which specific components exist, at what rate). The technical foundation - schema, `PayrollPeriod`/
`PayrollRun` lifecycle, RBAC, audit, idempotency - needs only that shape, not the content, and has
therefore been implemented (see "Revision: Payroll Phase 1 (Foundation) implemented" above) with
`pay_components` left empty and no amount/formula anywhere. **What genuinely still cannot begin**
without Section X approval is any code that gives `CompensationComponent.amount` or
`pay_components` a real value, or that computes a *real* `PayrollRunLine`/`Payslip` from them.
Phase 4's former cross-service blocker is separately resolved: Document Service's
workload-upload endpoint is now implemented and tested.

**Second revised finding (Phase 2):** the same reasoning extends one phase further than
originally thought. Phase 2's calculation *pipeline* - the code paths, strategy interfaces, and
lifecycle transitions - needed only the *shape* Section H already specified, not real
rates/formulas, and has therefore been implemented with every strategy resolving to a fixed,
pre-configured, non-statutory amount (Section H Revision). What genuinely still cannot happen is
any run producing a *meaningful* payroll number for a real employee - that still requires
Section X's pay-component catalogue content, statutory rates, and proration formula. Phase 3
(approval/finalization) is technically unblocked in the same sense: its permission/state-machine
mechanics are already implemented (Section D/J), pending only the same business content.

**Third revised finding (Phases 3–4 and Adjustment Runs):** the same reasoning extends through
Phase 4 and the adjustment-run flow. None of approval/finalization, payslip generation, or
adjustment-run creation required any Section X business content to build - they needed only the
*shape* Sections D/J/K/M/N already specified (the state machine, the maker-checker permission
split, the payslip content fields, the Document Service contract), and all of it has therefore
been implemented using only already-calculated `PayrollRunLine` data and the already-approved
Document Service workload-upload contract. What genuinely still cannot happen is unchanged from
the First/Second revised findings: no run anywhere - regular or adjustment - produces a
*meaningful* payroll number for a real employee without Section X's pay-component catalogue
content, statutory rates, and proration formula, and the adjustment/netting accounting policy
(Section K) remains exactly as undecided as before this revision.

## Z. Acceptance criteria

For the *technical* specification locked in this document (independent of the pending business
values):
- A `PayrollRun` cannot reach `FINALIZED` without passing through `PENDING_APPROVAL` →
  `APPROVED` (decision 7) — verified by a state-machine test rejecting any skip.
- No code path updates a `FINALIZED` run's `PayrollRunLine`/`Payslip` rows (decision 8) —
  verified by the absence of any such repository/service method.
- A correction is provable only via a new `PayrollRun` with `run_type = ADJUSTMENT` and
  `corrects_run_id` set (decision 9) — **implemented and verified** by
  `PayrollAdjustmentIntegrationTest` (Section K).
- `GET /payroll/payslips/*` never returns another employee's payslip to a self-scoped caller
  (decision 10) — **implemented and verified** by `PayslipIntegrationTest`, mirroring every other
  service's self-scope test pattern.
- The generated PDF contains every field listed in Section M — **implemented and verified** by
  `PayslipPdfGeneratorTest`.
- No payslip is ever stored as a Payroll-owned binary; every payslip has a `document_ref`
  (decision 13) — verified by schema (no binary column exists on `payslips`).
- Every state transition and every payslip view/download produces an audit entry (decision 14)
  — verified by an audit-emission test per transition/action.
- Duplicate "start run" and duplicate payslip generation are proven idempotent (decision 15) —
  verified by a duplicate-request test per Section T.
- Every produced event carries `eventVersion`, `correlationId`, and the other envelope fields
  every other service's events already carry (decision 16) — verified by schema/contract test.

**Added in this revision:**
- A run cannot be approved or finalized by the identity recorded as its `initiated_by` actor,
  even when that identity holds `payroll.approve` — verified by a self-approval-rejection test
  (Section J).
- The calculation pipeline's proration step is invoked through a named, swappable policy
  identifier, and the default (no-op) policy produces zero adjustment regardless of
  attendance/leave input — **implemented and verified** by a test asserting full pay under the
  default policy even with real attendance/leave input rows present (Section H). No second policy
  is registered yet, so a configuration-swap test (asserting a *different* policy is invoked) does
  not yet exist - there is nothing non-default to swap to until Section X supplies a real formula.
- An adjustment run's `PayrollRunLine`/`Payslip` rows are created without mutating any row of
  the run referenced by `corrects_run_id` — verified by a test asserting the original run's rows
  are byte-for-byte unchanged after an adjustment run completes (Section K).
- `payslip.generated.v1` is published exactly once per `Payslip` row, with a payload containing
  no amount/pay/tax value — **implemented and verified** by `PayslipIntegrationTest`'s outbox
  assertions (Section Q).

**Added in the Compensation Management revision:**
- Two `EmployeeCompensation` records with overlapping effective ranges for the same employee
  cannot both be `ACTIVE` — **implemented and verified** by a conflict-rejection test
  (`EmployeeCompensationServiceTest`, `CompensationManagementIntegrationTest`).
- A compensation record already read by a `FINALIZED` run's `PayrollRunLine` cannot be updated —
  **implemented and verified** by an immutability-rejection test.
- No endpoint accepts a client-supplied employee identity to read or write that employee's own
  compensation, pay-component catalogue entry, or statutory profile — **implemented and
  verified** by an authorization test asserting every such endpoint requires
  `payroll.process`/`payroll.read.all`, never a self-scoped grant.
- An `EmployeeStatutoryProfile` with status `NOT_APPLICABLE` never produces a missing-identifier
  exception regardless of whether an identifier is present — **implemented and verified** by a
  dedicated "not applicable is never flagged" test.
- An `EmployeeStatutoryProfile` with status `APPLICABLE`/`PENDING_VERIFICATION` and a blank
  identifier produces the corresponding `PayrollException` without blocking the employee's
  `PayrollRunLine` from being written — **implemented and verified**.
- Resolving a `PayrollException` is idempotent-safe: resolving an already-`RESOLVED` exception is
  rejected rather than silently re-applied — **implemented and verified**.
- All pre-existing `PayrollRunLine`/payslip-generation/adjustment-run tests continue to pass
  unmodified in behavior (two pre-existing tests' *expected exception counts* were updated to
  include the new, intentionally-additive `MISSING_STATUTORY_PROFILE` exception — not a
  regression, a documented consequence of the new check) — **verified**, full payroll-service
  suite green (89/89).

**Added by the Phase 2 revision:**
- A genuine calculation failure (an ambiguous/invalid compensation state, not a mock) rolls back
  every `PayrollRunLine`/`PayrollException` write from that attempt, and the run is left in
  `CALCULATION_FAILED` — verified by an integration test that triggers a real engine exception,
  asserts zero rows were persisted, then fixes the data and reprocesses the same run successfully.
- Reprocessing a run never creates duplicate `PayrollRunLine`/`PayrollException` rows — verified
  by both a database uniqueness constraint and an integration test reprocessing a rejected run.
- An employee with no effective compensation produces a `PayrollException`, not a blocked run —
  verified by an integration test asserting the run still reaches `CALCULATED` with a mixed
  line/exception result.
- `attendance.finalized.v1`/`leave.approved.v1` are consumed idempotently against a real broker,
  and a non-`FINALIZED` status on the attendance event type is ignored — verified by messaging
  integration tests mirroring the platform's existing event-idempotency test pattern.

**Added by the Payslip Generation revision:**
- Finalizing an `APPROVED` run generates exactly one `Payslip` per `PayrollRunLine`, uploads the
  real PDF bytes through Document Service's workload-upload contract, and publishes one
  `payslip.generated.v1` per payslip, with no salary/tax value in the payload — verified by
  `PayslipIntegrationTest`.
  - A per-employee generation failure is recorded in `PayslipGenerationFailure` without blocking
    other employees or affecting the already-`FINALIZED` run's status — verified by
    `PayslipGenerationServiceTest`.
  - Re-invoking `POST /payroll/runs/{id}/finalize` on an already-`FINALIZED` run is idempotent: no
    re-transition, no duplicate `payroll.processed.v1`, and only still-missing payslips are
    retried — verified by `PayrollIntegrationTest`/`PayslipIntegrationTest`.
- Self-only payslip access (`GET /payroll/payslips/me`, `/{id}`, `/{id}/download`) never returns
  another employee's payslip to a `payslip.read.self`-only caller, while `payslip.read.all`
  authorizes any payslip regardless of self — verified by `PayslipIntegrationTest`.

**Added by the Adjustment Run revision:**
- `POST /payroll/runs/{id}/adjustments` creates an `ADJUSTMENT` run only against a `FINALIZED`
  original, rejecting any other status, and never mutates the original run's row — verified by
  `PayrollAdjustmentIntegrationTest` and a unit test asserting the original object's state is
  unchanged after creation.
- A second adjustment against the same original is rejected while an earlier one has not yet
  reached `FINALIZED`, and is accepted once it has — verified by
  `PayrollAdjustmentIntegrationTest`.
- Self-approval prevention applies identically to an adjustment run (the identity that created it
  may not approve/reject/finalize it) — verified by `PayrollAdjustmentIntegrationTest`.
- An adjustment run's calculation reuses `PayrollCalculationEngine` unchanged, including signed
  (negative) component amounts flowing through to `gross_pay`/`net_pay` with no sign-flipping or
  netting — verified by `PayrollAdjustmentIntegrationTest`.
- An adjustment run's finalization produces its own independent `Payslip` and `payslip.
  generated.v1`, leaving the original run's `Payslip` row byte-for-byte unchanged — verified by
  `PayrollAdjustmentIntegrationTest`.

Full sign-off additionally requires every item in Section X to be resolved — this document
alone does not make Payroll "ready to build" in the business sense, only in the technical sense
described above. The three cross-document contract changes in Section X are technical
prerequisites for Phases 3–5 specifically and should be resolved before those phases begin,
independent of the business-approval timeline.
