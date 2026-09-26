# Payroll Service — Implementation-Ready Specification

This document locks the technical/product decisions listed below for Payroll Service and
defines the concrete specification needed to begin implementation. It builds on, and does not
contradict, `docs/DEVELOPMENT_ROADMAP.md`, `docs/architecture/MICROSERVICES.md`,
`docs/database/DATABASE.md`, `docs/api/API.md`, `docs/security/RBAC.md`,
`docs/architecture/COMMUNICATION.md`, `docs/ARCHITECTURE_REVIEW.md`, `docs/DECISIONS.md`,
`docs/workflows/WORKFLOWS.md`, and `docs/PAYROLL_REPORTING_DECISIONS.md`.

**Status: Phase 1 (Foundation) implemented; Phase 2 onward (calculation and every other
sensitive business function) remains gated.** A `payroll-service` module now exists implementing
only the technical scaffolding this document's Section Y calls "Foundation": `PayrollPeriod`/
`PayrollRun` lifecycle (Section D), RBAC (`payroll.process`/`payroll.approve`/`payroll.read.all`),
audit logging, and idempotency. It contains **no** salary/tax calculation, **no** seeded
pay-component catalogue, **no** compensation amount, **no** payslip generation, and **no**
Document Service integration. Every item in Section X remains PENDING_GDB_APPROVAL and
unimplemented; nothing in Section X has been resolved or assumed by this code. Every other
architecture document's "(deferred)" marking for Payroll now means specifically "calculation and
sensitive business functionality deferred," not "no code exists" - see each document's own
Payroll row for the precise split.

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
consistent with decisions 7–9:

```
DRAFT → CALCULATED → PENDING_APPROVAL → APPROVED → FINALIZED
                                      \→ REJECTED (returns to DRAFT for correction and re-calculation)
DRAFT/CALCULATED/PENDING_APPROVAL → CANCELLED (before finalization only)
```

- **DRAFT** — run created for a period; no calculation performed yet.
- **CALCULATED** — per-employee earnings/deductions computed from compensation + attendance/leave
  inputs; nothing is visible to employees yet.
- **PENDING_APPROVAL** — submitted for the approval decision required by decision 7.
- **APPROVED** — decision recorded; run may now be finalized.
- **FINALIZED** — terminal, immutable per decision 8. Payslips are generated and become visible
  to employees only at this point.
- **REJECTED** — sent back for correction; a rejected run never reaches FINALIZED.
- **CANCELLED** — abandoned before finalization; no payslips are ever generated.

A **FINALIZED** run's PayrollRun row and its per-employee results are never updated in place —
enforced at the service layer (no update path exists once `status = FINALIZED`) and reinforced
by not exposing any edit endpoint for a finalized run, mirroring how Workflow's `ApprovalTask`
and Document's `DocumentVersion` are already treated as append-only once decided.

## E. Payroll entities

Extends `DATABASE.md`'s documented `PayComponent; PayrollPeriod; PayrollRun; Payslip` with the
entities decisions 4–9 require to be actionable:

| Entity | Purpose | Status |
|---|---|---|
| `PayrollPeriod` | One calendar month (decision 3): year, month, start/end dates, cut-off date, status | Extends documented entity — fields newly specified here |
| `EmployeeCompensation` | Effective-dated compensation record per employee (decision 4): employee ref, currency (INR), effective-from/to, status | New — required by decision 4 |
| `CompensationComponent` | A single component (earning/deduction/employer-contribution) attached to an `EmployeeCompensation`, referencing the pending `PayComponent` catalogue | New — required by decision 4; catalogue contents PENDING |
| `PayComponent` | Catalogue master (code, name, type) | Documented in `DATABASE.md`; catalogue contents PENDING |
| `PayrollRun` | One run for one period: run type (REGULAR/ADJUSTMENT), corrects-run reference, status (Section D), initiated/approved/finalized actors and timestamps | Extends documented entity |
| `PayrollRunLine` | Per-employee calculated result within a run: earnings/deductions breakdown, gross pay, total deductions, net pay | New — required to support Section L/M payslip content |
| `Payslip` | One finalized, generated payslip per employee per run: employee ref, run ref, period ref, document ref, generated timestamp | Extends documented entity |

## F. Database tables and important fields

Following the platform's established conventions (`UUID` primary keys, `TIMESTAMPTZ`
timestamps, `created_at/created_by/updated_at/updated_by/version` audit columns, `*_ref` for
cross-service references, `*_id` for local foreign keys):

- **payroll_periods**: `id`, `year`, `month`, `start_date`, `end_date`, `cut_off_date`, `status`.
- **employee_compensations**: `id`, `employee_ref`, `currency` (fixed `INR` initially, decision 2),
  `effective_from`, `effective_to` (nullable = still active), `status`.
- **compensation_components**: `id`, `compensation_id FK`, `component_code`, `component_type`
  (`EARNING`/`DEDUCTION`/`EMPLOYER_CONTRIBUTION`), `amount` or `calculation_type` (fixed vs.
  formula-driven — formula source PENDING per Section X), `proration_policy_code` (nullable;
  identifies the configurable proration policy for attendance/leave-sensitive components,
  Section H — no formula is stored here, only a policy identifier).
- **pay_components**: `id`, `code`, `name`, `type`. Row contents (the actual catalogue) PENDING.
- **payroll_runs**: `id`, `period_id FK`, `run_type` (`REGULAR`/`ADJUSTMENT`), `corrects_run_id`
  (nullable, self-referencing FK, populated only for `ADJUSTMENT` runs), `status`,
  `initiated_by`, `approved_by`, `approved_at`, `finalized_at`.
- **payroll_run_lines**: `id`, `run_id FK`, `employee_ref`, `gross_pay decimal(14,2)`,
  `total_deductions decimal(14,2)`, `net_pay decimal(14,2)`, breakdown stored as structured
  JSON referencing `compensation_components` at calculation time (for reproducibility). No
  positivity constraint is applied to any amount column — adjustment-run lines may be negative
  (Section K).
- **payslips**: `id`, `employee_ref`, `run_id FK`, `period_id FK`, `document_ref`,
  `generated_at`.
- **outbox_events** / **processed_events**: identical shape to every other service in this
  platform (transactional outbox; inbox for the two consumed events in Section Q).

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

## H. Payroll calculation architecture

Per decision 5, calculation happens inside Payroll Service, not an external provider. The
calculation pipeline for a `PayrollRun`:

1. Resolve the `PayrollPeriod` being processed.
2. Resolve the set of employees included in the run (Section I).
3. For each included employee, resolve the `EmployeeCompensation` effective for that period.
4. Incorporate attendance/leave inputs (Section I) through a **configurable proration policy**
   (below) to determine any pay adjustment (e.g. for unpaid leave or loss-of-pay days).
5. Apply each `CompensationComponent` (earnings, deductions, employer contributions) per the
   pending catalogue and pending deduction/statutory rules.
6. Compute `gross_pay`, `total_deductions`, `net_pay` per employee, persisted as a
   `PayrollRunLine`.
7. On finalization only (Section J), generate one `Payslip` + PDF per `PayrollRunLine`, and
   publish `payslip.generated.v1` per payslip (Section Q).

### Configurable proration policy (technical model only)

The calculation pipeline must not hard-code any unpaid-leave/loss-of-pay formula. Step 4 is
implemented as a pluggable **proration policy** the run resolves per compensation component,
not a fixed calculation baked into the pipeline:

- A `ProrationPolicy` is a named, versioned strategy identifier (e.g. stored as a
  `proration_policy_code` on `CompensationComponent` or on `PayrollPeriod`, exact placement is
  an implementation detail) that the calculation pipeline looks up and invokes for
  attendance/leave-sensitive components. It receives the employee's resolved compensation, the
  relevant `AttendanceRecord`/`LeaveRequest` inputs for the period, and the period's calendar
  bounds, and returns an adjustment amount.
- **Default policy**: until GDB supplies a real formula, the only non-invented default is a
  **no-op policy** — full compensation is paid regardless of attendance/leave state. This is
  the sole default this document specifies, because paying full compensation is the absence of
  a rule, not the assertion of one; any other default (e.g. per-day deduction) would be
  inventing the exact business formula the task requires this document not to invent.
  <br>An actual formula (e.g. `unpaid_days × (monthly_rate / days_in_period)`) may only be
  configured once GDB Finance/Legal supplies it — see Section X.
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

Per decision 6, a payroll run's inputs are:
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
pattern already established by Document, Asset, and Workflow Service.

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

**`payroll.approve` does not exist in `RBAC.md` today.** Adding it is a required, minimal
addition to the existing permission catalogue — not a new capability invented from nothing, but
the same `<domain>.<verb>` naming shape every other permission in `RBAC.md` already follows
(e.g. `policy.publish`, `asset.assign`). It is tracked as a required cross-document change in
Section X, not applied to `RBAC.md` by this document.

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
Payroll's own Phase 4 (Section Y) can now integrate against this endpoint once Payroll Service
itself is built — no Payroll code was written by this pass.

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
phases (Section Y) beyond the business decisions above. Payroll Service itself still does not
exist in code — none of this pass built any part of it.

## Y. Implementation phases

1. **Foundation** — module scaffolding, security baseline, `PayrollPeriod`/`EmployeeCompensation`/
   `CompensationComponent`/`PayComponent` schema and CRUD needed only for internal setup (no
   catalogue content — that's pending).
2. **Calculation core** — `PayrollRun`/`PayrollRunLine` lifecycle (Section D), attendance/leave
   event consumption (Section I), calculation pipeline (Section H) — usable only once the
   pending proration formula and pay component catalogue are supplied.
3. **Approval/finalization** — Section J/K endpoints and state transitions, including the
   `payroll.approve` permission and self-approval prevention. The `RBAC.md` addition is now in
   place (Section X); this phase is otherwise unblocked at the documentation level.
4. **Payslip generation** — PDF content (Section M), Document Service integration (Section N/U)
   — and self-service access (Section B). Document Service's `POST /documents/workload-uploads`
   is now implemented and tested (Section X); this phase is otherwise unblocked at the
   cross-service level.
5. **Messaging** — `payroll.processed.v1` and `payslip.generated.v1` producers,
   `attendance.finalized.v1`/`leave.approved.v1` consumers (Section Q), platform wiring (gateway
   route, database init, per the established per-service pattern; next available port per
   existing convention). The `COMMUNICATION.md` addition is now in place (Section X); this phase
   is otherwise unblocked at the documentation level.
6. **Audit/security hardening** — Section R/S controls, verified before any real payroll data
   is processed.

**Revised finding:** the original wording above said Phase 1 could not begin coding at all until
the pay-component catalogue and statutory rule source (Section X) were approved. That was too
broad. Section F already fully specifies the *shape* `CompensationComponent`/`PayComponent` take
(component code, type, amount, proration-policy code) independent of the catalogue's *content*
(which specific components exist, at what rate). The technical foundation - schema, `PayrollPeriod`/
`PayrollRun` lifecycle, RBAC, audit, idempotency - needs only that shape, not the content, and has
therefore been implemented (see "Revision: Payroll Phase 1 (Foundation) implemented" above) with
`pay_components` left empty and no amount/formula anywhere. **What genuinely still cannot begin**
without Section X approval is any code that gives `CompensationComponent.amount` or
`pay_components` a real value, or that computes a `PayrollRunLine`/`Payslip` from them - i.e.
Phase 2 (calculation) onward. Phase 4's former cross-service blocker is separately resolved:
Document Service's workload-upload endpoint is now implemented and tested.

## Z. Acceptance criteria

For the *technical* specification locked in this document (independent of the pending business
values):
- A `PayrollRun` cannot reach `FINALIZED` without passing through `PENDING_APPROVAL` →
  `APPROVED` (decision 7) — verified by a state-machine test rejecting any skip.
- No code path updates a `FINALIZED` run's `PayrollRunLine`/`Payslip` rows (decision 8) —
  verified by the absence of any such repository/service method.
- A correction is provable only via a new `PayrollRun` with `run_type = ADJUSTMENT` and
  `corrects_run_id` set (decision 9) — verified by schema constraint plus service logic.
- `GET /payroll/payslips/*` never returns another employee's payslip to a self-scoped caller
  (decision 10) — verified by an access-guard test mirroring every other service's self-scope
  test.
- The generated PDF contains every field listed in Section M — verified by a payslip-content
  test once PDF generation exists.
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
  attendance/leave input — verified by a test asserting full pay under the default policy and a
  configuration-swap test asserting a different registered policy is actually invoked (Section H).
- An adjustment run's `PayrollRunLine`/`Payslip` rows are created without mutating any row of
  the run referenced by `corrects_run_id` — verified by a test asserting the original run's rows
  are byte-for-byte unchanged after an adjustment run completes (Section K).
- `payslip.generated.v1` is published exactly once per `Payslip` row, with a payload containing
  no amount/pay/tax value — verified by a messaging test mirroring Asset/Expense/Workflow's own
  event-content assertions (Section Q).

Full sign-off additionally requires every item in Section X to be resolved — this document
alone does not make Payroll "ready to build" in the business sense, only in the technical sense
described above. The three cross-document contract changes in Section X are technical
prerequisites for Phases 3–5 specifically and should be resolved before those phases begin,
independent of the business-approval timeline.
