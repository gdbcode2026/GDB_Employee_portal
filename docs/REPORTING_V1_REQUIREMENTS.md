# Reporting V1 — Minimum Mandatory Requirements

**Scope of this review**: a read-only, documentation-only inspection of the existing Reporting
capability (such as it is), the eight other implemented modules it would draw from, and the
frontend. **No application code was changed to produce this report.** Nothing here invents a GDB
business rule, a new permission, or a new architecture — every MUST HAVE conclusion below is
either (a) directly supported by an existing, already-granted RBAC permission or an existing
module capability, or (b) explicitly flagged as blocked on a GDB decision in Section G.

**Headline finding**: there is no Reporting service today — not even a stub (unlike Notification,
which started as a `501` placeholder, Reporting has no module directory, no Maven entry, no code
at all). Every document that mentions Reporting describes it the same way: a generic, deferred,
event-fed "report definition / report run / projection" engine, with **zero concrete report
named anywhere** in this repository's requirements. At the same time, nearly every other module
already exposes self/team/all-scoped, filterable list APIs — the exact same shape the existing
Dashboard (`frontend/employee-portal/app/page.tsx`) already uses to build a personal "report" by
calling those APIs directly, with no reporting backend at all. That existing pattern, not a new
BI engine, is what this review recommends extending for V1.

---

## 1. What exists today

### 1.1 `reporting-service` (code)

**Does not exist.** `backend/pom.xml`'s `<modules>` list has no `reporting-service` entry, and no
such directory exists anywhere under `backend/`. This is a step further behind than every other
deferred/partial module (Notification had a real, running `501` stub service before this
engagement's Notification V1 work; Reporting has nothing to build on top of).

### 1.2 `docs/REQUIREMENTS_TRACEABILITY.md`

Exactly one row mentions Reporting, bundled with Audit:

> `Audit/reporting | Audit, Reporting* | audit/report routes | AuditEntry, Projection | all domain events | audit/report permissions | append-only, redaction, projection lag/export scope tests`

No business requirement row exists for Reporting on its own, and no row names a single concrete
report (contrast with every other row, which names the actual entities/events/permissions of a
real, scoped feature). This confirms Reporting was never given its own articulated business need
— it was documented as "whatever Audit needs plus a projection/export capability," not as a
product requirement with content.

### 1.3 `docs/architecture/MICROSERVICES.md`

- Ownership table: `Reporting | Read-optimized cross-domain reports | report definitions,
  projections, generated report metadata`.
- Boundary rule: *"Reporting is read-only and never becomes a transactional source of truth."*
- Contract table: `Reporting (deferred) | Report APIs; consumes approved events | None required |
  No transactional write-back. Projection lag must be visible.`

"Report definitions" + "projections" + "report runs" is a generic BI-engine shape: an admin
defines a parameterized report once, users "run" it against a materialized projection, projection
staleness must be surfaced. This is the exact shape the task's scope rules say not to build
("do NOT build a BI platform... do NOT introduce a new reporting architecture unless the existing
requirements justify it") — and nothing in this repository justifies it, because no concrete
report has ever been specified.

### 1.4 `docs/api/API.md`

> `Reporting* | GET /reports, POST /reports/{id}/runs | Scoped report/run; report.read.self/team/all, report.export; filters/report parameters validated against approved definition.`

Marked `*`: *"documented contracts only and remain unavailable until their deferred domains are
approved."* Same generic definition/run shape as above — no named report, no response schema.

### 1.5 `docs/database/DATABASE.md`

> `Reporting (deferred) | Projection; ReportDefinition; ReportRun | projection source event/data
> JSON; definition name/query spec; run definition FK/requester/status/output ref | Export
> restricted/audited.`

Same generic engine again, now with a concrete (but still contentless) schema: a dynamic
query-spec-driven report definition, a run with a status and an output reference (implying
async/exportable generation). Building this literally, for V1, would mean inventing a dynamic
report/query builder nobody has asked for — explicitly out of scope per this task's rules.

### 1.6 `docs/security/RBAC.md`

The permission catalogue already defines `report.read.self/team/all` and `report.export`. The
role mapping table (the only place that says who actually gets them) reads:

| Role | Report-related grant (verbatim) |
|---|---|
| Employee | *(none — "Own profile, attendance/WFH/leave/expense/document/asset request records; own payslip, notifications, goals and assigned tasks.")* |
| Team Lead | *(none — "Employee permissions plus team read, task coordination, and only approval permissions when chosen by a configured workflow.")* |
| Manager | *"...team employee/attendance/leave/expense/performance/report permissions and team approvals."* |
| HR | *"...HR document/policy/workflow/performance/report permissions."* |
| Finance | *"...plus finance reporting; no default HR-profile write."* |
| Admin | *(none — "Identity/role/operational configuration permissions; no automatic HR or payroll data access.")* |

This is the single most important, concrete fact in this review, and it corrects the task's own
framing: **the roles actually entitled to any report today are Manager (team-scoped), HR, and
Finance — not "Admin" (explicitly excluded from HR/payroll data) and not Team Lead (no report
grant at all), and not Employee by default.** See Section C.

### 1.7 Existing module capabilities (Employee, Organization, Attendance, Leave, Payroll, Expense,
Project, Performance, Notification)

Catalogued every `@GetMapping` across all nine services. Every one of them is a filterable,
paginated list/detail endpoint (`{items, page:{number,size,total}}`), gated by that module's own
`*.read.self/team/all`. None exposes a pre-aggregated metric today. Three findings matter most:

- **The "team" scope is already a cross-service resolved aggregate, not a per-ID lookup.**
  Verified directly in `AttendanceService.resolvePage`: when a caller omits `employeeId` and
  holds only `attendance.read.team`, the service resolves `scope.allowedIds()` — the caller's
  entire team, from Organization — and returns every one of those employees' records in one
  paginated call. The same self/team/all `AccessGuard` pattern (confirmed present in Leave,
  Expense, Performance, Payroll) means **a Manager or HR caller can already pull "my whole team's
  attendance/leave/expense/performance records" from each owning service today, with zero new
  backend code**, exactly as `GET /attendance` with no `employeeId` already proves.
- **Payroll already returns run-level aggregate counts, but no money totals.**
  `PayrollRunDtos.Response` already carries `employeeCount`, `lineCount`, `exceptionCount` per
  run — a real, already-implemented report field. But there is no `GET
  /payroll/runs/{id}/lines` endpoint and no gross/net/deduction total anywhere in any payroll
  response, so a "payroll cost" number does not exist today at any granularity. This is the one
  concrete gap this review found in an otherwise-sufficient set of existing APIs (Section F).
- **The existing Dashboard (`app/page.tsx`) is already proof of the right pattern.** It composes
  `GET /employees/me`, `/attendance/me`, `/leave/balances/me`, `/workflows/tasks/me`,
  `/performance/goals` directly, server-side, with no "reporting" backend involved at all. This is
  not a gap to fix — it is the existing, working precedent this review recommends reusing for
  Manager/HR/Finance views, scaled up to team/all scope using permissions that already exist.

### 1.8 Frontend reporting/dashboard requirements

One dashboard exists (`/`), and it is personal/self-scoped only (see 1.7). `lib/nav.ts` has no
Manager/HR/Finance-specific navigation group and no "Reports" entry at all. There is no documented
or coded expectation anywhere in the frontend for a cross-employee report view. This confirms
Reporting V1's frontend need is a genuinely new (small) set of pages, not a retrofit.

---

## A. What reporting functionality is explicitly required by existing project requirements

Taken literally, the *only* explicitly documented requirement is the generic, contentless
`GET /reports` / `POST /reports/{id}/runs` contract (Section 1.3–1.5) plus the `report.read.*`/
`report.export` permission shapes (Section 1.6). No specific report name, metric, or filter is
specified anywhere. Read narrowly, "what's required" is almost nothing; read for *intent*, the
RBAC role-mapping table is the strongest signal of real intent, because it is the only place that
ties "report" language to a specific, already-approved role capability (Manager/team,
HR/performance+general, Finance/finance-specific). Section C builds the minimum mandatory set from
that intent, not from the empty `ReportDefinition` schema.

## B. What's already available through existing module APIs (do not duplicate)

- **Every module's own self-view** (payslips, leave balance, attendance, goals, notifications,
  assigned tasks) — already fully implemented per-module; an "Employee report" would be pure
  duplication (see Section C).
- **Every module's team/all-scoped list, for Manager/HR callers**, already returns the complete
  team's/organization's raw records (Section 1.7) — no new list/filter API is needed for any
  MUST HAVE report below; only aggregation of what's already returned.
- **Organization's `GET /organization/chart`** already returns the department→team structure
  needed to group any headcount/attendance/leave figure by department or team.
- **Payroll's run-level counts** (`employeeCount`/`lineCount`/`exceptionCount`) are already a
  usable "payroll run summary," just not a cost summary.
- **Expense claims already carry `total`/`currency` per claim**, so a Finance expense summary is
  pure aggregation of an already-complete list response — no new expense-service API needed.

## C. Minimum mandatory Reporting V1 reports

Corrected per Section 1.6's evidence: **HR**, **Finance**, and **Manager** (not "Admin," not
"Team Lead" — neither is granted a report permission in RBAC.md today). **Employee: not
required** — see its own entry below for why.

| # | Report | Role(s) | Classification |
|---|---|---|---|
| 1 | Workforce / Headcount Summary | HR | **MUST HAVE V1 — implemented, with two confirmed limitations** (see "Implementation note" under D1) |
| 2 | Leave Balance & Utilization Summary | HR, Manager (team-scoped) | **MUST HAVE V1 — implemented** (see "Implementation note" under D2) |
| 3 | Payroll Cost Summary | Finance | MUST HAVE V1 (blocked on one small Payroll addition — see Section F/G) |
| 4 | Expense Summary | Finance | **MUST HAVE V1 — implemented, with a confirmed authorization gap** (see "Implementation note" under D4) |
| 5 | Team Overview | Manager | **MUST HAVE V1 — implemented** (see "Implementation note" under D5) |
| — | Employee self-report | Employee | **NOT REQUIRED NOW** — fully covered by existing `/me` endpoints + the existing Dashboard; RBAC grants Employee no `report.*` permission by default, and inventing one here would be adding scope nobody asked for. |

## D. Per-report specification

### D1. Workforce / Headcount Summary (HR) — MUST HAVE V1

- **Purpose**: how many employees GDB has, broken down by department/team and status — the single
  most basic, universally-needed HR operational fact, and the natural anchor report for the role
  RBAC.md says gets "report permissions."
- **Allowed roles**: HR (`report.read.all` or `report.read.team`/`.all` per Section G's open
  question); never Employee/Manager-scoped in this report specifically.
- **Data source**: Employee Service (`GET /employees?status=`) only — see the implementation note
  below for why Organization Service was dropped entirely, not just its filter parameters.
- **Filters**: employment status (`ACTIVE`/`INACTIVE`) only — see limitations below for why
  department, team, and employment-type filtering are not implemented.
- **Metrics implemented**: total headcount; headcount by employment status (Active/Inactive).
  Department/team/employment-type breakdowns are **not implemented** — see limitations.
- **API needed**: none — two `GET /employees` calls (`?size=1` for the grand total, which also
  doubles as the authorization anchor, and `?status=ACTIVE&size=1`), reading only `page.total`
  from each. Inactive is derived by subtraction (the `EmployeeStatus` enum has exactly two values,
  confirmed in `employee-service`'s own domain enum, so this is exact, not an estimate).
- **Frontend**: one new report page, composing the two calls above.

**Implementation note (built)**: implemented at
`frontend/employee-portal/app/workforce-summary/page.tsx`, added to the "Overview" nav group, no
backend changes. Authorization uses the same anchor technique as D2/D5 (`GET /employees`
401s/403s correctly; Employee and Team Lead are reliably excluded) — see "Confirmed gap: HR vs.
Manager cannot be distinguished by the frontend" below for the one requirement this does **not**
fully satisfy.

**Confirmed gap (much deeper than the D5 correction): there is no employee-to-department/team
linkage anywhere in this system, not just a missing filter parameter.** Investigated Organization
Service's actual controllers directly (not just the chart endpoint):
- `DepartmentController`/`TeamController` are pure structure CRUD (name/code/parent/status) with
  **no employee-membership list or count on either entity, anywhere**.
- `GET /organization/chart` (`OrganizationChartService.buildChart`) confirmed to build its
  response purely from `DepartmentRepository`/`TeamRepository` — department/team names and IDs
  only, zero employee references, zero counts.
- The thing every other report in this document calls "team scope" (`employee.read.team`,
  `attendance.read.team`, etc.) is **a completely different concept from Organization's "Team"
  entity** — it is resolved via `ReportingRelationController`'s `GET
  /organization/reporting-relations/scope/{managerEmployeeRef}`, i.e. the manager-reporting
  hierarchy, not org-chart team membership. The two happen to share the word "team" but share no
  data model.
- Employee Service's own `Employee`/`Employment` records carry no department or team reference
  field at all (confirmed against both the full `EmployeeDtos.Response` and `Summary` shapes).

**Net result: no existing API, in any combination, can answer "how many employees are in
department X / team Y."** This is not a missing filter parameter to work around - it is a genuine
absence of the underlying data relationship. Per this task's explicit instruction, no backend
endpoint was added and no unsafe workaround (e.g., inferring membership from unrelated data) was
built; department/team breakdown is simply **not implemented**, and is documented here as a real
product gap: closing it would require either Employee Service recording a department/team
reference per employee, or Organization Service recording per-team employee membership - a
business/data-model decision for GDB, not a reporting-layer decision.

**Also not implemented: employment-type breakdown.** `employmentType` exists only on the
single-employee detail response (`GET /employees/{id}`'s `employment.employmentType`), never on
the list/summary response. Computing a breakdown would require one detail call per employee -
explicitly the "unsafe frontend workaround" this task's instructions forbid building, since cost
scales with total headcount rather than a small fixed N. Not implemented; documented here instead.

**Gap fixed — Reporting V1 authorization review, Part A
(docs/REPORTING_AUTHORIZATION_REVIEW.md), implemented**: HR vs. Manager is now distinguished.
`GET /employees`'s response carries a `page.scope` field (`"TEAM"`/`"ALL"`), taken directly from
`EmployeeAccessGuard.ListScope.unrestricted()` - no new permission, no change to
`EmployeeAccessGuard` itself. This page now requires `scope === "ALL"` before rendering, so a
Manager's legitimate 200 (team-scoped) no longer satisfies this HR-only report. No content-based
heuristic was used.

**Critical, separate, pre-existing bug discovered while adding regression tests for the above —
FIXED.** A real-database (Testcontainers) test of `GET /employees` with no `query` parameter -
exactly how this page, Team Overview, and Leave Summary all call it - threw a Postgres
`could not determine data type of parameter` 500 from
`EmployeeRepository.searchAll`/`searchWithinScope`'s `(:query is null or lower(...) like ...)`
JPQL pattern. Root cause: the standalone `:query is null` check gave Postgres's extended query
protocol no type context for that parameter, and it was observed to resolve it as `bytea`,
producing `function lower(bytea) does not exist` once that untyped value reached `concat()`/
`lower()`. **Fix**: every occurrence of `:query` (and, defensively, `:status`) is now wrapped in
`cast(:param as string)`, including the standalone `is null` check, so Postgres always has a
single, consistent, correctly-typed parameter regardless of null-ness - no change to filtering
semantics (no `query` still means no filtering) or to authorization. Confirmed fixed via real
PostgreSQL/Testcontainers: `EmployeeIntegrationTest` now has dedicated coverage for `GET /employees`
with no `query`, with a non-empty `query`, and for empty-string `query` being treated identically
to no filter (9/9 passing). This report's, Team Overview's, and Leave Summary's employee-roster
call pattern (`GET /employees` with no `query`) is now confirmed to execute without error.

### D2. Leave Balance & Utilization Summary (HR org-wide; Manager team-scoped) — MUST HAVE V1

- **Purpose**: who has how much leave left, and how much has been taken, per leave type — a
  recurring, unavoidable HR/manager operational need (headcount planning, liability tracking,
  approving further leave) and the clearest match to RBAC's explicit "...leave...report
  permissions" language for both Manager and HR.
- **Allowed roles**: HR (all employees), Manager (own team only, via the already-existing
  `leave.read.team` scope resolution).
- **Data source**: Leave Service only — `GET /leave/balances?employeeId=` (team/all scope) for
  current balances, `GET /leave/requests?status=&from=&to=` (team/all scope) for utilization
  within a period.
- **Filters**: leave type, period/financial year, department/team (HR only), employee status.
- **Metrics**: allocated/used/reserved/available per employee/leave type; aggregate used-days per
  team/department/leave type for a selected period.
- **API needed**: none new — both endpoints and their team/all scope resolution already exist.
- **Frontend**: one new report page; Manager sees it scoped to their own team automatically
  (same access-guard behavior as every other team-scoped page in this portal); HR sees the
  unscoped version.

**Implementation note (built)**: implemented at `frontend/employee-portal/app/leave-summary/page.tsx`,
added to the "Work" nav group, no backend changes. Authorization uses the exact same anchor
technique as D5 (Team Overview): `GET /employees` 401s/403s correctly (plain employees and Team
Leads get `PermissionDeniedNotice`, not the report), and both HR and Manager hold
`employee.read.team`/`.all` alongside their own `leave.read.team`/`.all` grant per RBAC.md's role
bundling, so gating on Employee's authorization reliably predicts Leave's — the leave data itself
remains independently scoped by `LeaveAccessGuard.resolveListScope` on every call, never overridden
by this page.

**Scope correction found while implementing**: `GET /leave/requests` turned out to be unnecessary.
Reading `LeaveBalance`'s own Javadoc directly confirmed `reserved` is *"units held by SUBMITTED
requests (not yet decided)"* and `used` is *"units consumed by APPROVED requests"* — i.e.
`GET /leave/balances` alone already carries both the "current balance" and the "utilization"
figures D2 asked for, aggregated and point-in-time-consistent, with no second endpoint, no date-
range-overlap semantics to infer, and no period-filter UI to invent beyond the `periodYear` the
balance already carries. **Implemented using `GET /leave/balances` + `GET /leave/types` (names)
+ `GET /employees` (names/scope-gating) only** — not `GET /leave/requests`.

One consequence: the known `LeaveRequestStatus` frontend/backend status mismatch (the real
"pending" value is `SUBMITTED`; `lib/api/types.ts` incorrectly says `"PENDING"`) did **not** need
correcting for this report, since it never calls `/leave/requests`. Left as-is, still flagged for
whoever eventually builds a request-level report (e.g. a future `WORKFLOW_COMPLETED`-adjacent
view) that actually needs it.

**Also descoped, consistent with "Data source: Leave Service only"**: department-level grouping.
D2's own metric line mentions "per team/department," but a department breakdown needs
Organization Service data this section explicitly does not list as a data source — adding it would
have meant a new cross-service call beyond D2's own declared scope. Implemented grouping is by
leave type only (org-wide for HR, team-scoped for Manager, per the access guard) — still exactly
what the "aggregate used-days per ... leave type for a selected period" metric asks for, just
without the optional department dimension.

### D3. Payroll Cost Summary (Finance) — MUST HAVE V1, implemented

- **Purpose**: total payroll cost (gross pay, deductions, employer contributions, net pay) per
  processed payroll run — the single most essential Finance reporting need in any payroll system,
  and the direct referent of RBAC's "...plus finance reporting" grant.
- **Endpoint**: `GET /api/v1/payroll/runs/cost-summary?periodId=&status=&page=&size=&sort=` — a
  dedicated reporting endpoint, deliberately not a reuse of `GET /payroll/runs`'s response
  contract (that contract is unchanged by this work). Matches the existing `GET
  /api/v1/payroll/runs/*` security matcher as a literal path segment (resolved by Spring MVC ahead
  of the `/{id}` variable route, same convention as `/employees/me`) — **no `SecurityConfig`
  change was needed**.
- **Authorization**: `payroll.read.all` only — the existing, already-exclusive flat permission
  (no self/team tier exists for payroll runs at all). No new permission was introduced; Section
  G's "finance reporting" naming question is resolved for D3 specifically by reusing
  `payroll.read.all` directly. An Employee, Manager, or Admin token holds none of it and is
  denied with 403 server-side — confirmed by real-PostgreSQL integration tests, not by a frontend
  check.
- **Filters implemented**: `periodId` (optional, exact match) and `status` (optional, **defaults
  to `FINALIZED`** when omitted — only finalized runs represent real paid amounts). Both are plain
  derived-query filters (`findByStatus`/`findByPeriodIdAndStatus`), branched on in the service
  layer by whether `periodId` was supplied — deliberately not a JPQL `(:periodId is null or ...)`
  pattern, to avoid the exact PostgreSQL parameter-type-inference defect fixed elsewhere in
  Reporting V1 (see D1's and D4's notes above, and
  `docs/REPORTING_AUTHORIZATION_REVIEW.md`). Department/team/employee filters are not supported —
  consistent with D1/D4's finding that no employee-to-department/team linkage exists anywhere in
  this system, and payroll runs have no per-employee dimension at this report's level anyway.
- **Metrics implemented**: `totalGrossPay`, `totalDeductions`, `totalEmployerContributions`,
  `totalNetPay` — each a straight `sum(...)` of the already-materialized `PayrollRunLine` column
  of the same name for that run. No formula is computed or invented. **There is deliberately no
  combined "total payroll cost" field** — no such formula is documented anywhere in this codebase
  (checked: no "cost to company"/CTC/"total cost" concept exists in the Payroll calculation
  engine or requirements), so none was added; adding one would require GDB to supply the exact
  formula first.
- **Adjustment runs shown separately**: REGULAR and ADJUSTMENT runs are never merged or netted.
  Each FINALIZED run — original or correction — is returned as its own row, carrying its own
  `runType`/`correctsRunId`. A period with a correction applied therefore shows two rows, each
  with its own totals (an adjustment run's totals can be negative, per Section K's signed-line
  model) — not one combined period-level total. This was an explicit GDB decision (D3 Phase 1
  review), chosen specifically to avoid inventing an adjustment-netting rule nowhere documented.
- **Response shape**: a dedicated `PayrollCostSummaryDtos.Response` record — `runId`, `periodId`,
  `periodYear`, `periodMonth`, `runType`, `correctsRunId`, `status`, `employeeCount`, the four
  money totals above, `finalizedAt`. No employee-level field appears anywhere in it (no salary,
  deduction, payslip, bank, or statutory data) — confirmed by a dedicated regression test
  asserting the response body never contains an employee reference or any payslip/compensation
  field name.
- **Known limitation**: this report is per-run, not per-period. A period with both a finalized
  REGULAR and a finalized ADJUSTMENT run will show as two rows; computing one netted period total
  would require inventing an aggregation rule this task was explicitly told not to invent. If GDB
  later wants a single period-level total, that is a separate, explicit decision.
- **Frontend**: `frontend/employee-portal/app/payroll-summary/page.tsx` — period and status
  filters (the only two the backend supports), stat cards for gross pay/deductions/employer
  contributions/net pay, and a per-run table. No "Total Payroll Cost" metric is shown, matching
  the backend. Handles 401/403/other API failure/empty result/success, the same pattern as every
  other Reporting V1 page; a 403 alone is sufficient here (no `page.scope` field exists or is
  needed, since the backend gate is already flat/unambiguous).

### D4. Expense Summary (Finance) — MUST HAVE V1

- **Purpose**: total submitted/approved/reimbursed expense amounts per period/department — the
  other half of Finance's "finance reporting" grant, and a direct reconciliation need once
  `expense.reimburse` has been exercised.
- **Allowed roles**: Finance (`expense.read.all` already covers the data; report view reuses it).
- **Data source**: Expense Service only — `GET /expenses/claims?from=&to=&size=200` (no
  `employeeId`, no `status` filter — fetched once, unfiltered by status, so every metric below is
  computed from one call).
- **Filters implemented**: date range (`from`/`to`) only. **Currency and department/team were
  dropped** — see the implementation note below.
- **Metrics implemented**: claim count and rejected count (date-range, currency-agnostic); per
  currency — submitted total+count, approved total+count, reimbursed total+count, rejected count.
  Interpretation used (documented, not assumed silently): "total claimed"/"approved"/"reimbursed"
  each map 1:1 onto claims **currently** in that exact status (`SUBMITTED`/`APPROVED`/`REIMBURSED`
  respectively) within the selected date range — not a cross-status funnel/history calculation,
  since a claim's past statuses aren't retained anywhere to compute one.
- **API needed**: none — every claim already carries `total`/`currency`/`status`; this is pure
  client-side aggregation of an already-complete list response.
- **Frontend**: one new report page.

**Implementation note (built)**: implemented at
`frontend/employee-portal/app/expense-summary/page.tsx`, added to the "Work" nav group, no backend
changes. No Employee Service call is made at all - D4's metrics are aggregate totals/counts, never
per-employee, so there is no name to resolve (unlike D1/D2, which needed Employee Service for
exactly that reason).

**Department/team filtering dropped, same root cause as D1**: there is no employee-to-department/
team linkage anywhere in this system (confirmed in D1's own note) - grouping expense claims by
department would need the same nonexistent join. Not implemented; see D1 for the full finding.
**Currency as a filter was also dropped** in favor of a per-currency breakdown table (same pattern
D2 used for leave type) - showing every currency present at once is strictly more informative than
forcing a single-currency filter, and avoids ever summing two different currencies together.

**Gap fixed — Reporting V1 authorization review, Part A
(docs/REPORTING_AUTHORIZATION_REVIEW.md), implemented**: `GET /expenses/claims`'s response now
carries a `page.scope` field (`"SELF"`/`"TEAM"`/`"ALL"`), computed from the guard's own decision
*before* the query runs (correct even on an empty result - the exact scenario that made the
content-based heuristic considered and rejected below unsafe). Implementing this required a small,
additive extension to `ExpenseAccessGuard.ListScope` itself: `unrestricted`/`allowedIds` alone
could not distinguish self from team (both collapse to the same "restricted" shape), so a `Tier`
enum was added, set from exactly the same three branches `resolveListScope` already had - no
authorization *rule* changed, no permission added or removed. This page now requires
`scope === "ALL"` before rendering, so neither a plain Employee/Team Lead's self-scoped 200 nor a
Manager's team-scoped 200 satisfies this Finance-only report any longer.

**Why a content-based heuristic was rejected instead** (kept for the record): "does the response
contain more than one distinct `employeeRef`" would have incorrectly blocked a legitimate Finance
user whenever the selected date range happened to contain claims from only one employee -
including the ordinary, fully-legitimate empty-result case. The `page.scope` fix above avoids this
entirely by reading the authorization decision itself, never the data.

**Critical, separate, pre-existing bug discovered while adding regression tests for the above —
FIXED.** A real-database (Testcontainers) test of `GET /expenses/claims` with `from`/`to` set and
`status` omitted - exactly how this page calls it - threw a Postgres
`could not determine data type of parameter` 500 from `ExpenseClaimRepository.searchAll`'s
optional-filter JPQL. Root cause (two-stage): the same standalone `(:param is null or ...)` pattern
caused the same type-inference failure as D1's `:query` bug; wrapping `:from`/`:to` in
`cast(:param as date)` fixed `searchAll` directly but exposed a second, different failure
(`cannot cast type bytea to date`) in `searchForEmployee`/`searchWithinScope` specifically -
confirmed via Hibernate SQL/bind-parameter trace logging to be Hibernate falling back to binding
the cast-wrapped null parameter as untyped `JAVA_OBJECT` whenever the same query also has an
unrelated `employeeRef` equality/`IN` clause. **Fix**: replaced the standalone `is null` check
entirely, for all three repository methods, with `coalesce(:param, <concrete sentinel>)` -
`coalesce(:from, cast('0001-01-01' as date))`, `coalesce(:to, cast('9999-12-31' as date))`, and a
self-referential `coalesce(:status, c.status)` for the enum filter - so every parameter occurrence
is always paired with a concretely-typed value and Postgres never needs to resolve an unanchored
type. No change to filtering semantics (absent `from`/`to`/`status` still means no filtering on
that dimension) or to authorization. Confirmed fixed via real PostgreSQL/Testcontainers:
`ExpenseIntegrationTest` now has dedicated coverage for `from`+`to` with no `status` (this report's
exact pattern, including the empty-result case), no `from`/`to`, `status` alone, and all three
together (16/16 passing). This report's own default call pattern is now confirmed to execute
without error.

### D5. Team Overview (Manager) — MUST HAVE V1

- **Purpose**: a manager's single cross-cutting view of their own team right now — who's checked
  in today, who has a pending leave/expense request awaiting them, team headcount. This is the
  Manager-equivalent of the existing personal Dashboard, scoped to team instead of self, and is
  the most directly "production-useful" of the five since it supports a daily workflow rather
  than periodic review.
- **Allowed roles**: Manager only (own team, via existing `*.read.team` scope resolution).
- **Data source**: Attendance (`GET /attendance?from=&to=`, team scope, no `employeeId` → full
  team), Leave (`GET /leave/requests?status=SUBMITTED`, team scope), Expense (`GET
  /expenses/claims?status=SUBMITTED`, team scope), Employee (`GET /employees?status=ACTIVE`, team
  scope, no team/department parameter — see D1's correction note) for headcount/roster and to
  resolve the bare `employeeRef` UUIDs the other three APIs return into display names.
- **Filters**: date (defaults to today for attendance).
- **Metrics**: team headcount; count checked-in/checked-out/not-checked-in today; count of pending
  leave requests; count of pending expense claims.
- **API needed**: none — every call and its team-scope resolution already exists and was verified
  directly in `AttendanceService`/`LeaveRequestService`/`ExpenseClaimService`/`EmployeeAccessGuard`
  (Section 1.7).
- **Frontend**: one new page, reusing the existing Dashboard's card/list components verbatim.

**Implementation note (built)**: implemented at `frontend/employee-portal/app/team-overview/page.tsx`,
added to the "Overview" nav group, no backend changes. Authorization is entirely backend-enforced:
the page's first call (`GET /employees?status=ACTIVE`) 401s for an unauthenticated caller and 403s
for anyone without `employee.read.team`/`.all` (plain employees and Team Leads included, per
RBAC.md Section 1.6) — the page shows a dedicated "Managers only" notice for the 403 case rather
than a generic error, and never sends a client-supplied team/manager identifier anywhere. Two
discoveries worth recording for D1–D4:
- `GET /leave/requests`'s real "pending" value is `status=SUBMITTED`, not `PENDING` — the
  frontend's own `LeaveRequestStatus` type (`lib/api/types.ts`) incorrectly lists `"PENDING"`
  instead, which also means the existing `/leave` page's "Cancel" button condition
  (`request.status === "PENDING"`) can never match real data. Left unfixed here (out of this
  task's scope - it does not affect Team Overview, which queries the raw string directly), but
  will need correcting before/while building D2.
- Attendance/Leave/Expense list responses carry only a bare `employeeRef` UUID, never a name —
  Team Overview resolves names by cross-referencing the Employee roster call's results client-side
  by ID. Every future report that lists per-employee activity (D1, D2) will need the same pattern.

## E. Is a dedicated reporting database/read model actually necessary for V1?

**No.** Every MUST HAVE report above is answerable either by composing today's already-correct,
already-real-time self/team/all list APIs (exactly as the existing Dashboard already does), or —
for the one genuine gap (Payroll cost totals) — by a narrow addition to the *owning* service's
existing response, not a new store. A dedicated reporting database buys exactly one thing none of
these reports need yet: fast cross-service joins at meaningfully large scale. Nothing in this
repository suggests GDB's employee/department/transaction volumes are anywhere near where N
small, direct API calls (the same pattern the Dashboard already uses today) would be too slow.
Building the `ReportDefinition`/`ReportRun`/`Projection` engine the docs describe now would mean
standing up event consumption, a projection store, and a query/export engine to serve five reports
that don't need any of it — precisely the BI-platform over-build this task's scope rules forbid.

## F. Are existing service APIs/events sufficient?

**Yes, for all five.** D1, D2, D4, and D5 needed no new API and no new event consumption — only
new frontend pages composing existing, already-permissioned endpoints. D3 (Payroll Cost Summary)
needed one small, same-service addition — a new `GET /payroll/runs/cost-summary` endpoint and a
dedicated response DTO (Section D3), both implemented — not a new API *service*, not a new event,
not a new permission. No report in this set consumes any domain event at all, which also means
`docs/architecture/COMMUNICATION.md`'s event-contract table is unaffected by this review.

## G. Missing business decisions that block implementation

1. **Exact `report.*` permission grants per role are not fully named.** RBAC.md's role table
   describes Manager/HR/Finance report access narratively ("...report permissions," "...finance
   reporting") but never states, e.g., whether Finance's grant is `report.read.all` scoped to
   payroll/expense data, a new finance-specific permission, or simply `payroll.read.all` +
   `expense.read.all` reused directly (which would mean no new `report.*` permission is even
   needed for V1, and the generic `report.read.self/team/all`/`report.export` catalogue entries
   stay unused/deferred). This must be confirmed before implementation, even though it does not
   change *what* the reports show.
2. **Whether Payroll should expose any aggregate cost figure at all** (Section D3) — **resolved**:
   GDB/Finance explicitly directed this report's implementation (D3 Phase 1 review and final
   decisions), confirming `payroll.read.all` is sufficient and no new permission is needed. D3 is
   now implemented on that basis.
3. **Export format/destination for `report.export`** (CSV? PDF? download vs. email?) is undefined
   anywhere. Not needed for the five MUST HAVE reports' on-screen value; should stay deferred
   until asked for.
4. **Historical range / retention for reports** (how many past payroll periods, leave years,
   expense periods should be queryable) is not documented anywhere and is a straightforward GDB
   policy input, not a technical blocker — V1 can default to "whatever each owning service
   already retains" without inventing a retention rule.

None of these blocked D1/D2/D4/D5 (no permission-naming ambiguity affects what data a Manager/HR
caller already can see today). Item #2 (D3's policy question) is now resolved — see above. Item
#1 (the broader `report.*` permission-naming question) remains open but is not a blocker for any
of the five MUST HAVE reports, all of which reuse existing business-domain permissions directly.

## H. Classification summary

| Item | Classification |
|---|---|
| D1 Workforce/Headcount Summary (HR) | **MUST HAVE V1 — implemented** (total + status breakdown only; department/team/employment-type breakdown remain documented, unimplemented gaps; HR-vs-Manager authorization gap **fixed** by Reporting V1 authorization review Part A) |
| D2 Leave Balance & Utilization Summary (HR/Manager) | **MUST HAVE V1 — implemented** |
| D3 Payroll Cost Summary (Finance) | **MUST HAVE V1 — implemented** (per-run gross pay/deductions/employer contributions/net pay via a dedicated `GET /payroll/runs/cost-summary` endpoint, gated by existing `payroll.read.all`; REGULAR/ADJUSTMENT runs shown as separate rows, never netted; no "total payroll cost" field - no formula is documented for one) |
| D4 Expense Summary (Finance) | **MUST HAVE V1 — implemented** (date-range + per-currency/status totals; Finance-exclusive gating **fixed** by Reporting V1 authorization review Part A; a separate, unrelated pre-existing query bug remains - see D4) |
| D5 Team Overview (Manager) | **MUST HAVE V1** |
| Employee self-report | **NOT REQUIRED NOW** — duplicates existing `/me` endpoints + Dashboard |
| Generic `ReportDefinition`/`ReportRun`/`Projection` engine | **NOT REQUIRED NOW** — no concrete report has ever justified it; would be over-build |
| Dedicated reporting database/read model | **NOT REQUIRED NOW** — see Section E |
| `report.export` (CSV/PDF export) | **OPTIONAL** — no report above needs it to be useful; revisit if GDB asks |
| Performance review completion tracking | **OPTIONAL** — plausible future HR report, no documented requirement forces it into V1 |
| Attendance exception/anomaly report | **OPTIONAL** — no documented requirement; risks inventing a policy (what counts as an "exception") |
| Asset utilization / Project burn-down reports | **NOT REQUIRED NOW** — no RBAC report grant or documented need ties to these domains |

---

## Final summary

1. **Exact mandatory reports**: (1) Workforce/Headcount Summary, (2) Leave Balance & Utilization
   Summary, (3) Payroll Cost Summary, (4) Expense Summary, (5) Team Overview.
2. **Exact roles allowed**: HR → #1, #2 (org-wide); Finance → #3, #4; Manager → #2 (team-scoped),
   #5. No report for Employee or generic "Admin" (neither is granted report access in RBAC.md).
3. **Data sources required**: Employee Service only (#1 — Organization Service was dropped
   entirely during implementation; see D1's note, there is no employee-to-department/team
   linkage anywhere to join against); Leave Service (#2); Payroll Service (#3, needs one small
   same-service addition — run-level cost totals); Expense Service only (#4 — no Employee Service
   call either, since D4's metrics are aggregate, never per-employee); Attendance + Leave +
   Expense + Employee Services (#5). No service outside this list is needed.
4. **Is a reporting service/database needed?** **No.** D1/D2/D4/D5 are served by composing
   existing self/team/all-scoped APIs server-side in new frontend pages (the same pattern the
   existing personal Dashboard already uses). D3 is served by one dedicated, same-service
   endpoint added to Payroll (`GET /payroll/runs/cost-summary`). No new service, no new database,
   no new event consumption, for any of the five.
5. **Implementation status**: ~~Team Overview (Manager)~~, ~~Leave Balance & Utilization
   Summary~~, ~~Workforce / Headcount Summary~~, ~~Expense Summary~~, and ~~Payroll Cost
   Summary~~ — **all five done** (see D5's, D2's, D1's, D4's, and D3's "Implementation note"/
   detail above). ~~Two authorization gaps~~ — **both fixed** by Reporting V1 authorization review
   Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md): `GET /employees` and `GET /expenses/claims`
   now carry a `page.scope` field taken directly from each service's own access-guard decision,
   and D1/D4 fail closed on anything other than `scope === "ALL"`. ~~A real-database test exposed
   a pre-existing, unrelated Postgres parameter-type-inference bug in `EmployeeRepository`'s and
   `ExpenseClaimRepository`'s optional list-filter JPQL~~ — **FIXED** (separate follow-up task;
   see D1's and D4's notes above for the exact root causes and fixes). `EmployeeRepository` now
   casts every `:query`/`:status` occurrence, including the standalone `is null` check, to a
   concrete type; `ExpenseClaimRepository` now uses `coalesce(:param, <sentinel>)` instead of a
   standalone `is null` check for `:from`/`:to`/`:status`. Both confirmed against real
   PostgreSQL/Testcontainers (Employee: 9/9, Expense: 16/16 passing). ~~Whether Payroll should
   expose any aggregate cost figure at all~~ — **resolved and implemented** (D3, above): GDB
   directed the implementation, confirming `payroll.read.all` is sufficient. No authorization,
   business behavior, or RBAC changed by any of this work. **Remaining optional items**: Section
   G item #1 (the broader `report.*` permission-naming question — not a blocker for any shipped
   report) and Part B (dedicated `report.workforce.read`/`report.expense.read` permissions,
   optional hardening, not required).
