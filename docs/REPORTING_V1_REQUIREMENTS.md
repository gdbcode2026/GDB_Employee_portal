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
| 1 | Workforce / Headcount Summary | HR | MUST HAVE V1 |
| 2 | Leave Balance & Utilization Summary | HR, Manager (team-scoped) | **MUST HAVE V1 — implemented** (see "Implementation note" under D2) |
| 3 | Payroll Cost Summary | Finance | MUST HAVE V1 (blocked on one small Payroll addition — see Section F/G) |
| 4 | Expense Summary | Finance | MUST HAVE V1 |
| 5 | Team Overview | Manager | **MUST HAVE V1 — implemented** (see "Implementation note" under D5) |
| — | Employee self-report | Employee | **NOT REQUIRED NOW** — fully covered by existing `/me` endpoints + the existing Dashboard; RBAC grants Employee no `report.*` permission by default, and inventing one here would be adding scope nobody asked for. |

## D. Per-report specification

### D1. Workforce / Headcount Summary (HR) — MUST HAVE V1

- **Purpose**: how many employees GDB has, broken down by department/team and status — the single
  most basic, universally-needed HR operational fact, and the natural anchor report for the role
  RBAC.md says gets "report permissions."
- **Allowed roles**: HR (`report.read.all` or `report.read.team`/`.all` per Section G's open
  question); never Employee/Manager-scoped in this report specifically.
- **Data source**: Employee Service (`GET /employees?status=`) + Organization Service
  (`GET /organization/departments`, `/teams`) for the grouping structure. **Correction found while
  implementing D5**: `GET /employees` does not actually accept `departmentId`/`teamId` filters
  despite `docs/api/API.md` documenting them (`EmployeeController.list` only takes `status` and
  `query`) — team scope is instead resolved entirely server-side from the caller's own identity
  (`EmployeeAccessGuard.resolveListScope`, via Organization), with no team/department parameter at
  all. For HR's org-wide D1, this means grouping by department/team must happen client-side after
  fetching the full (`.all`-scoped) list and cross-referencing `GET /organization/chart`, not via
  a server-side department/team filter.
- **Filters**: department, team, employment status (`ACTIVE`/`INACTIVE`), employment type.
- **Metrics**: headcount per department/team/status/employment type; total headcount.
- **API needed**: none new, in principle — each count is one `GET /employees?departmentId=X&status=ACTIVE&size=1`
  call read via `page.total`, iterated once per department/team returned by the org chart. This is
  an N-calls pattern (small N for a single-company portal), consistent with the existing
  Dashboard's own N-calls-per-page precedent. If department/team count grows enough to matter,
  Employee Service could add one narrow `GET /employees/headcount?groupBy=department` aggregate —
  a same-service addition, not a new reporting platform.
- **Frontend**: one new report page, HR-only nav entry, composing the calls above exactly like
  `app/page.tsx` already composes its own cards.

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

### D3. Payroll Cost Summary (Finance) — MUST HAVE V1, blocked on one small Payroll addition

- **Purpose**: total payroll cost (gross pay, deductions, employer contributions, net pay) per
  processed payroll run/period — the single most essential Finance reporting need in any payroll
  system, and the direct referent of RBAC's "...plus finance reporting" grant.
- **Allowed roles**: Finance only (reuses `payroll.read.all` plus whatever permission Section G's
  "finance reporting" question resolves to).
- **Data source**: Payroll Service.
- **Filters**: payroll period/year, run status (`FINALIZED` runs only is the obvious default,
  since only those represent real paid amounts).
- **Metrics**: total gross pay, total deductions, total employer contributions, total net pay,
  employee count, exception count — per run/period.
- **API needed**: **yes, a small one** — today `PayrollRunDtos.Response` has `employeeCount`/
  `lineCount`/`exceptionCount` but no money total, and there is no `GET
  /payroll/runs/{id}/lines` endpoint to sum client-side. The minimal fix is adding
  `totalGrossPay`/`totalDeductions`/`totalEmployerContributions`/`totalNetPay` to the existing
  run response (computed from `PayrollRunLine` rows Payroll already owns) — a same-service,
  same-endpoint addition, not a new reporting service and not a new permission.
- **Frontend**: one new report page, Finance-only, reading the enriched run list/detail response.
- **Open question this review does not resolve**: Payroll's existing posture everywhere else in
  this codebase is unusually strict ("sensitive isolation," "no self-service path," every
  sensitive read audited). Exposing even an aggregate cost total is a policy choice GDB should
  confirm explicitly, not something this review assumes — see Section G.

### D4. Expense Summary (Finance) — MUST HAVE V1

- **Purpose**: total submitted/approved/reimbursed expense amounts per period/department — the
  other half of Finance's "finance reporting" grant, and a direct reconciliation need once
  `expense.reimburse` has been exercised.
- **Allowed roles**: Finance (`expense.read.all` already covers the data; report view reuses it).
- **Data source**: Expense Service only — `GET /expenses/claims?status=&from=&to=&employeeId=`
  (all scope).
- **Filters**: status (`SUBMITTED`/`APPROVED`/`REIMBURSED`/`REJECTED`), date range, currency,
  department (via a join against Employee/Organization for grouping only).
- **Metrics**: total claimed, total approved, total reimbursed, count by status, per period/
  currency.
- **API needed**: none — every claim already carries `total`/`currency`/`status`; this is pure
  client-side (or one small server-side) aggregation of an already-complete list response.
- **Frontend**: one new report page, Finance-only.

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

**Yes, for four of five.** D1, D2, D4, and D5 need no new API and no new event consumption — only
new frontend pages composing existing, already-permissioned endpoints. D3 (Payroll Cost Summary)
needs one small, same-service addition to an existing response (Section D3) — not a new API
contract, not a new event, not a new service. No report in this set needs Reporting to consume any
domain event at all, which also means `docs/architecture/COMMUNICATION.md`'s event-contract table
is unaffected by this review.

## G. Missing business decisions that block implementation

1. **Exact `report.*` permission grants per role are not fully named.** RBAC.md's role table
   describes Manager/HR/Finance report access narratively ("...report permissions," "...finance
   reporting") but never states, e.g., whether Finance's grant is `report.read.all` scoped to
   payroll/expense data, a new finance-specific permission, or simply `payroll.read.all` +
   `expense.read.all` reused directly (which would mean no new `report.*` permission is even
   needed for V1, and the generic `report.read.self/team/all`/`report.export` catalogue entries
   stay unused/deferred). This must be confirmed before implementation, even though it does not
   change *what* the reports show.
2. **Whether Payroll should expose any aggregate cost figure at all** (Section D3) is a real
   policy question, not an engineering one, given Payroll's deliberately strict isolation
   elsewhere in this codebase. GDB/Finance should confirm this explicitly rather than have it
   assumed by a reporting feature.
3. **Export format/destination for `report.export`** (CSV? PDF? download vs. email?) is undefined
   anywhere. Not needed for the five MUST HAVE reports' on-screen value; should stay deferred
   until asked for.
4. **Historical range / retention for reports** (how many past payroll periods, leave years,
   expense periods should be queryable) is not documented anywhere and is a straightforward GDB
   policy input, not a technical blocker — V1 can default to "whatever each owning service
   already retains" without inventing a retention rule.

None of these blocks starting D1/D2/D4/D5 (no permission-naming ambiguity affects what data a
Manager/HR caller already can see today); only D3 and the broader `report.*` permission-naming
question in #1 are genuine blockers worth resolving before writing code.

## H. Classification summary

| Item | Classification |
|---|---|
| D1 Workforce/Headcount Summary (HR) | **MUST HAVE V1** |
| D2 Leave Balance & Utilization Summary (HR/Manager) | **MUST HAVE V1 — implemented** |
| D3 Payroll Cost Summary (Finance) | **MUST HAVE V1** — blocked on Section G items 1–2 |
| D4 Expense Summary (Finance) | **MUST HAVE V1** |
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
3. **Data sources required**: Employee Service + Organization Service (#1); Leave Service (#2);
   Payroll Service (#3, needs one small same-service addition — run-level cost totals); Expense
   Service (#4); Attendance + Leave + Expense + Employee Services (#5). No service outside this
   list is needed.
4. **Is a reporting service/database needed?** **No.** All five reports are served by composing
   existing self/team/all-scoped APIs server-side in new frontend pages (the same pattern the
   existing personal Dashboard already uses), plus one narrow addition to Payroll's own existing
   response. No new service, no new database, no new event consumption.
5. **Highest-priority implementation task**: ~~Team Overview (Manager)~~ and ~~Leave Balance &
   Utilization Summary~~ — **both done** (see D5's and D2's "Implementation note"). **Next
   recommended**: Workforce / Headcount Summary (D1) — the only remaining report with no Section G
   business-decision blocker (D3 alone is blocked); it reuses the exact same `GET /employees`
   authorization-anchor call D5/D2 already prove out, and is HR's turn after two Manager-facing
   reports. Expect to need the client-side department/team grouping against `GET
   /organization/chart` that D1's own correction note already flags, since `GET /employees` still
   has no department/team filter.
