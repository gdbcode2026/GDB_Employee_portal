# Reporting V1 — Authorization Design Review

**Scope of this review**: authorization only, for the four implemented Reporting V1 pages (D1
Workforce/Headcount Summary, D2 Leave Balance & Utilization Summary, D4 Expense Summary, D5 Team
Overview) plus the still-blocked D3 Payroll Cost Summary. **No application code was changed to
produce this review.** Every conclusion below is traced to a specific file read during this
review (`SecurityConfig`, the relevant `*AccessGuard`, `RBAC.md`), not assumed.

---

## 1. Current authorization model

Every business service follows the same shape: a deny-by-default Spring Security resource
server (`SecurityConfig`), gated by flat `hasAuthority`/`hasAnyAuthority` path matchers, backed by
an authority list the JWT's `permissions`/`roles` claims populate. Three distinct **guard shapes**
exist today, and the distinction between them is the root cause of every gap found in this review:

| Shape | Services | Behavior for a caller with only the baseline/self-tier permission |
|---|---|---|
| **Two-tier, self excluded** | Employee (`EmployeeAccessGuard`), Attendance, Performance | `resolveListScope` returns `denied()` unless the caller holds `.team` or `.all` — a self-only caller gets a flat **403** on the list endpoint. |
| **Three-tier, self included** | Leave (`LeaveAccessGuard`), Expense (`ExpenseAccessGuard`) | `resolveListScope` returns `restrictedTo(Set.of(self))` (**200**, their own records only) for a caller holding only `.self`. Never 403s a baseline employee. |
| **Flat, no self/team/all split** | Payroll (`SecurityConfig` path matchers directly) | `GET /payroll/runs` requires `payroll.read.all` outright — no guard class, no self/team tier exists on this endpoint at all. An employee token with any number of `payroll.read.self`/`payslip.*` authorities has none of the authorities this matcher requires and is denied. |

In every shape, `team` is resolved the same way: `OrganizationClient.resolveTeamScope(self)` →
`GET /organization/reporting-relations/scope/{managerEmployeeRef}` — never a client-supplied
team/department identifier. This part of the model is sound and is **not** a finding of this
review.

RBAC.md already declares a generic reporting permission family, unused by any code today:
`report.read.self/team/all`, `report.export` (line 26 of the catalogue). No role is mapped to it
in the role table (lines 28–36), and `docs/REPORTING_V1_REQUIREMENTS.md` Section G already flagged
this as an open question before this review.

**Current frontend reporting pages (D1/D2/D4/D5) do not use any dedicated reporting permission.**
Each composes an existing business-service endpoint directly and relies on whatever
self/team/all/flat behavior that endpoint already has:

| Report | Anchor call | Guard shape used | Frontend gate today |
|---|---|---|---|
| D1 Workforce | `GET /employees` | Two-tier (self excluded) | 401/403 on the anchor call only |
| D2 Leave Summary | `GET /employees` (same anchor) + `GET /leave/balances` | Two-tier anchor, three-tier data call | 401/403 on the **Employee** anchor call only |
| D4 Expense Summary | `GET /expenses/claims` | Three-tier (self included) | 401 only — no 403 path is reachable |
| D5 Team Overview | `GET /employees` (same anchor) + attendance/leave/expense | Two-tier anchor, three-tier data calls | 401/403 on the **Employee** anchor call only |

---

## 2. Confirmed security gaps

### Gap 1 — D1 Workforce: HR vs. Manager indistinguishable (confirmed, moderate)

`GET /employees` returns **200 for both** an HR caller (`employee.read.all` → `ListScope.all()`,
org-wide) and a Manager caller (`employee.read.team` → `ListScope.restrictedTo(team)`). The JSON
response (`{items, page:{number,size,total}}`) carries no field stating which branch produced it.
The Workforce report's own anchor-call gate (reused from D5/D2) only distinguishes "allowed at
all" from "denied" — it reliably excludes Employee and Team Lead (both get a flat 403, since
neither holds `employee.read.team`/`.all`), but **cannot** additionally exclude Manager, who is a
legitimate 200 on the exact same endpoint.

**Blast radius if unfixed**: a Manager visiting `/workforce-summary` sees their own team's
headcount, mislabeled as an org-wide HR figure. This is a **data-framing** problem, not a data
**leak** — a Manager never sees headcount beyond their own already-authorized team scope.

### Gap 2 — D4 Expense: Employee/Team Lead/Manager indistinguishable from Finance (confirmed, severe)

`GET /expenses/claims` uses the **three-tier** guard: `expense.read.self` is accepted on equal
standing with `.team`/`.all`. Every employee holds baseline `expense.read.self` (RBAC.md line 30),
so this endpoint **never 403s a plain Employee or Team Lead** — it returns 200 with their own
claims. Unlike Gap 1, there is no 403 at all to anchor on for the self tier; the ambiguity is
three-way (self/team/all), not two-way.

**Blast radius if unfixed**: any authenticated employee can open `/expense-summary` and see a
page labeled "Expense Summary" showing their own claim totals — again framing, not a leak (the
backend never returns another employee's claim to a self-scoped caller), but this is the one case
in this document where the *unintended audience* is the **entire workforce**, not just one
adjacent role (Manager). This is the most severe of the two gaps.

### Gap 3 (new, found during this review) — D5 Team Overview: HR partially, not fully, excluded

Not previously documented. `GET /employees` is the same two-tier anchor D5 uses — HR holds
`employee.read.all`, so HR *can* open `/team-overview` and will see `page.total` = the **entire
organization's** headcount under a "Team size" label. However, HR holds none of
`attendance.read.*`/`leave.read.*`/`expense.read.*` per RBAC.md's HR row (only "leave
all/approval" is named, and that is `leave.read.all` used by D2, not the attendance/expense
grants D5 also needs) — so the other three panels independently 403 and degrade to "Unavailable"
(caught by the page's existing `safe()` wrapper). **Net effect: HR sees one mislabeled stat card,
not a working leaked report.** Classified as low severity — see Section 9.

### Not a gap — D3 Payroll Cost Summary

`GET /payroll/runs` requires `payroll.read.all` outright, with **no self/team fallback at all**
(flat guard shape, Section 1). Per RBAC.md, this is granted to Finance, and to HR only "if
separately granted" (line 33) — i.e. not by default. No other role is named. **This endpoint
already has the property Gaps 1–2 are missing: a 200 response from it is already an unambiguous
signal.** D3 is blocked only by the already-documented missing cost-total field
(`docs/REPORTING_V1_REQUIREMENTS.md` D3) and the Section G permission-naming question below — not
by an authorization-detection gap.

### The Section G question this review was asked to resolve

`docs/REPORTING_V1_REQUIREMENTS.md` Section G asked whether Finance's "finance reporting" grant
is `report.read.all`, a bespoke permission, or just reuse of `payroll.read.all`/`expense.read.all`.
**Resolved by this review**: reuse of the existing business-domain `.all` permission is sufficient
and already correctly scoped for D3 and (modulo Gaps 1–2's detection problem) for D1/D4 too — see
Section 3.

---

## 3. Recommended permission model

**Core finding: the root cause of Gaps 1 and 2 is not a missing permission — it is a missing
signal.** HR already uniquely holds `employee.read.all`; Finance already uniquely holds
`expense.read.all` (and `payroll.read.all`). No one else holds either `.all` grant per RBAC.md's
role table. The problem is purely that the **existing, already-correct** authorization decision
(`ListScope.unrestricted()`) is computed server-side and then discarded — never serialized — so
the frontend has no way to read it back.

This review recommends a **two-part, additive fix**, scaled to exactly the two confirmed gaps:

### Part A (mechanism — required for both gaps): expose the scope decision

Add one small, additive field to the page metadata already returned by the two affected list
endpoints, reflecting the `ListScope` the guard already computed:

```
"page": { "number": 0, "size": 20, "total": 42, "scope": "ALL" }   // or "TEAM" / "SELF"
```

- Computed from information the backend already has (`ListScope.unrestricted()`
  /`allowedIds().size()` vs. the self-tier branch) — no new business logic, no new authorization
  *rule*.
- Pure JSON addition: existing consumers (Directory, Team Overview's other panels, the `/leave`
  and `/expenses` self-service pages) ignore the new field; nothing that reads `items`/`page.total`
  today breaks.
- Critically, this is computed from the **authorization decision**, not the **data result** — so
  it is correct even when zero records match (the exact case that made a content-based heuristic
  unusable for D4 in the prior implementation task).
- Applies to: Employee Service (`GET /employees`, fixes Gap 1 and Gap 3) and Expense Service
  (`GET /expenses/claims`, fixes Gap 2).

### Part B (boundary — recommended, not strictly required for today's role grants): dedicated permissions

The task asked whether Reporting should have "its own explicit authorization boundary," separate
from reusing a business-domain's `.all` permission. Part A alone makes Gaps 1/2 *detectable*;
it does not give Reporting a boundary *independent* of `employee.read.all`/`expense.read.all` — if
GDB ever grants a third role either of those `.all` permissions for an unrelated reason (e.g. an
Identity/provisioning tool needing `employee.read.all`), that role would silently also unlock the
Workforce/Expense reports. Part B closes that indirection:

Introduce exactly **two** new permissions — not the five the task listed as candidates to
evaluate. **D2 (Leave Summary), D5 (Team Overview), and D3 (Payroll Cost Summary) do not get a new
permission** — see the per-report table in Section 4 and the reasoning in Section 9.

| New permission | Grants | Why this shape |
|---|---|---|
| `report.workforce.read` | Org-wide access to the Workforce/Headcount report, equivalent in effect to holding `employee.read.all` **for this one purpose** | Follows the existing flat-capability convention (`<domain>.<action>`, cf. `payroll.process`, `expense.reimburse`, `policy.publish`) — not a self/team/all triad, because this report only ever has one meaningful tier (org-wide). |
| `report.expense.read` | Org-wide access to the Expense Summary report, equivalent in effect to holding `expense.read.all` **for this one purpose** | Same reasoning — Expense Summary has exactly one intended tier (Finance, org-wide). |

**Naming convention decided and why the task's other three candidates were rejected**:
- `report.workforce.read` / `report.expense.read` — **adopted**, matching the existing
  `<domain>.<action>` flat-capability shape used everywhere a permission has no self/team/all
  tier (`payroll.process`, `expense.reimburse`, `organization.manage`, `policy.publish`).
- `report.leave.read` — **rejected**. D2 genuinely needs two tiers (Manager=team, HR=all), unlike
  the other four reports. Forcing it into a single flat permission would either over-grant Manager
  org-wide access or under-grant HR. D2 already has a correct, non-ambiguous mechanism using
  *existing* permissions (Section 4) — introducing a new one here would be change for its own sake,
  contradicting "prefer additive... smallest change."
- `report.team.read` (for D5 Team Overview) — **rejected**. D5's current Manager-only scoping is
  already correct in the one dimension that matters (no attendance/leave/expense data leak — see
  Gap 3), and the one cosmetic gap (HR seeing a mislabeled headcount card) doesn't justify a new
  permission; Part A's `scope` field is sufficient if GDB wants it tightened. See Section 9.
- `report.payroll.read` (for D3) — **rejected**. `payroll.read.all` is already a clean, exclusive,
  flat permission with no self/team ambiguity (Section 2, "Not a gap"). Introducing a parallel
  permission for the exact same boundary would duplicate, not clarify, the access model — exactly
  the kind of unnecessary new surface the task's scope rules warn against. If GDB later wants
  "can see payroll cost reports" decoupled from "can see/process raw payroll runs," that is worth
  revisiting then, as its own decision — not a V1 reporting-authorization requirement.

---

## 4. Exact role-to-permission mapping

| Report | Permission(s) checked | Role(s) granted | Scope | New or existing? |
|---|---|---|---|---|
| D1 Workforce | `employee.read.all` **or** `report.workforce.read` (Part B); `GET /employees` response's `page.scope` must read `"ALL"` (Part A) | HR | Organization-wide only | `employee.read.all` existing; `report.workforce.read` **new** |
| D2 Leave Summary | `employee.read.team`/`.all` (anchor) + `leave.read.team`/`.all` (data) — **unchanged** | Manager (team), HR (all) | Team *or* all, both legitimate | Existing only — no new permission |
| D3 Payroll Cost Summary (not yet built) | `payroll.read.all` — **unchanged** | Finance (HR only if separately granted, per RBAC.md line 33) | Organization-wide only | Existing only — no new permission |
| D4 Expense Summary | `expense.read.all` **or** `report.expense.read` (Part B); `GET /expenses/claims` response's `page.scope` must read `"ALL"` (Part A) | Finance | Organization-wide only | `expense.read.all` existing; `report.expense.read` **new** |
| D5 Team Overview | `employee.read.team`/`.all` (anchor) + `attendance.read.team`/`leave.read.team`/`expense.read.team` (data) — **unchanged** | Manager (team) | Team only | Existing only — no new permission |

Admin is granted neither new permission, consistent with RBAC.md's existing "no automatic HR or
payroll data access" rule (line 35). Super Admin receives both implicitly via "all permissions"
(line 36), unchanged.

---

## 5. Required backend changes

Scoped to exactly the two confirmed gaps; D2/D3/D5 need **zero** backend changes.

1. **Employee Service** (`employee-service`):
   - `EmployeeDtos`/`PageResponse`'s page-metadata record: add a `scope` field (`"ALL"`/`"TEAM"`),
     set from the existing `EmployeeAccessGuard.ListScope.unrestricted()` boolean the guard already
     computes in `EmployeeService.list()`. No change to `EmployeeAccessGuard` itself unless Part B
     is adopted (see next bullet).
   - *If Part B is adopted*: `SecurityConfig`'s `GET /employees` matcher gains `report.workforce.read`
     as an additional accepted authority (`hasAnyAuthority("employee.read.team", "employee.read.all",
     "report.workforce.read")`); `EmployeeAccessGuard.resolveListScope()` gains one additional
     `hasAuthority(authentication, "report.workforce.read")` branch returning `ListScope.all()`,
     inserted alongside (not replacing) the existing `employee.read.all` branch.
2. **Expense Service** (`expense-service`):
   - `ExpenseClaimDtos`/`PageResponse`'s page-metadata record: add the same `scope`
     (`"ALL"`/`"TEAM"`/`"SELF"`) field, set from `ExpenseAccessGuard.ListScope`.
   - *If Part B is adopted*: `SecurityConfig`'s `GET /expenses/claims` matcher gains
     `report.expense.read`; `ExpenseAccessGuard.resolveListScope()` gains the matching branch,
     alongside (not replacing) the existing `expense.read.all` branch.
3. **RBAC.md**: add `report.workforce.read`, `report.expense.read` to the permission catalogue
   (line 26) and to HR's / Finance's rows (lines 33/34) respectively — *only if Part B is adopted*.
4. **No changes anywhere else**: Organization Service, Attendance Service, Leave Service,
   Payroll Service, Notification Service, Workflow Service are untouched. No new service, no new
   database, no new authorization service, no change to any `*AccessGuard`'s self or team branch.

---

## 6. Required frontend changes

1. `app/workforce-summary/page.tsx`: after the existing 401/403 handling, add one check —
   if the response's `page.scope !== "ALL"`, render `PermissionDeniedNotice` instead of the report
   (replaces today's "any 200 is good enough" gate).
2. `app/expense-summary/page.tsx`: same pattern — add a `page.scope !== "ALL"` check after the
   existing 401 handling (there is currently no reachable 403 branch to extend, since the
   three-tier guard never 403s a baseline employee; the new check becomes the **first** real
   role-boundary check this page has).
3. `lib/api/types.ts`: add `scope?: "SELF" | "TEAM" | "ALL"` to `PageMeta` (optional, so every
   other page using `PageMeta` — Directory, Leave Summary's roster call, Team Overview — compiles
   unchanged and simply never reads the new field).
4. **No changes** to `app/leave-summary/page.tsx` or `app/team-overview/page.tsx` logic (Section 9)
   — only benefit incidentally from the same `scope` field being present on `GET /employees`'s
   response if GDB later decides Gap 3 is worth closing (optional, not part of this plan).

---

## 7. Migration / backward-compatibility considerations

- **Additive only, in both directions.** The new `page.scope` field is purely additive JSON — no
  existing field is renamed, removed, or retyped. The new permissions (Part B) are purely additive
  grants — no existing permission is removed or redefined, and no existing role loses anything
  it currently has.
- **No behavior change for any endpoint's existing callers.** `employee.read.team`,
  `employee.read.all`, `employee.read.self`, `expense.read.self`, `expense.read.team`,
  `expense.read.all` all continue to resolve exactly as they do today — Part B only *adds* an
  alternative grant path alongside them, never replaces or narrows them, directly honoring the
  task's "do not change existing employee/expense behavior just to make reporting work."
- **Rollout order matters for Part A, not Part B.** Deploy the backend `scope` field first (safe
  on its own — it's inert until a consumer reads it), then deploy the frontend checks that consume
  it. If deployed in the other order, the frontend checks would see `scope === undefined` — this
  should **fail closed** (treat missing/unrecognized `scope` as not-ALL, i.e. deny), not fail open.
- **No data migration.** Nothing here touches a database schema, a stored record, or an event
  contract. Both changes are request/response-shape and authorization-rule additions only.
- **No impact on Section G's resolution for D3.** D3 can proceed independently of whether Part B
  is adopted (Section 10).

---

## 8. Security risks

- **If Part A is skipped but Part B is adopted alone**: no improvement. Without the `scope` field,
  the frontend still cannot tell which authority matched a `hasAnyAuthority` check, so granting
  `report.workforce.read`/`report.expense.read` without also exposing `scope` leaves Gaps 1/2 open
  exactly as today. **Part A is the load-bearing fix; Part B is the hardening on top of it.**
- **Fail-open risk if the frontend check is written carelessly.** The new frontend checks must
  treat "`scope` missing or not exactly `"ALL"`" as deny, never "assume ALL unless told otherwise."
  This is a standard allow-list-not-deny-list risk, called out explicitly so it isn't mis-implemented.
- **Residual risk accepted, not fixed, by this plan**: Gap 3 (HR seeing a mislabeled Team Overview
  headcount number) remains, by design (Section 9) — this is a conscious, documented acceptance,
  not an oversight.
- **No risk of weakening existing security.** Every change in this plan is additive; nothing here
  removes a 403, widens a self/team boundary, or accepts a client-supplied scope identifier at any
  point. The "do not accept client-supplied employee/team/manager identifiers" rule that already
  holds across every report page is unaffected by this plan.
- **No new attack surface.** No new service, no new network path, no new credential type. The
  `scope` field is derived server-side from data already protected by the existing JWT-validated
  authority check; it cannot be influenced by request input.

---

## 9. What should NOT be changed

- **Do not touch `EmployeeAccessGuard`'s, `LeaveAccessGuard`'s, or `ExpenseAccessGuard`'s existing
  self/team branches.** Every fix in this plan is a pure addition alongside them.
- **Do not add a `departmentId`/`teamId` filter to `GET /employees` or any grouping dimension to
  Organization Service.** Out of scope for this review (already fully documented in
  `docs/REPORTING_V1_REQUIREMENTS.md`'s D1 section) and unrelated to authorization.
- **Do not modify D2 (Leave Summary) or D5 (Team Overview) authorization.** Both are already
  correctly scoped for their *actual* audiences (HR+Manager for D2; Manager for D5, modulo Gap 3's
  cosmetic exception). Adding permissions here would be unjustified new surface for problems that
  don't exist.
- **Do not change `payroll.read.all`, or add a `report.payroll.read`, for D3.** Already a clean,
  exclusive, flat permission (Section 2). D3's actual blockers are unrelated to authorization
  (Section 10).
- **Do not build a reporting backend, a reporting database, a new authorization service, or a
  generic self/team/all-scoped `report.read.*` mechanism applied uniformly across all five
  reports.** Each report's correct tier set is different (Section 3); a one-size-fits-all
  `report.read.team/all` pair would either over-grant or under-grant at least one report, which is
  precisely how Gaps 1/2 arose in the first place by over-relying on a shared, generically-tiered
  endpoint.
- **Do not fix Gap 3 as part of this plan.** Flagged, scoped, and explicitly left for GDB to decide
  is worth closing — see below.

**On Gap 3 specifically**: closing it fully would mean either (a) also gating `GET /employees`'s
unrestricted branch so HR's `employee.read.all` doesn't satisfy Team Overview specifically (not
possible without the same Part A/B machinery, applied a third time, for a report whose only
actual leak-relevant panels — attendance/leave/expense — are *already* correctly excluded for HR),
or (b) accepting the cosmetic mislabeling as-is. This review recommends (b): the cost of a third
`scope`-based check is identical to Gaps 1/2's, for a finding whose only consequence is one
mislabeled stat card, never a data leak. Revisit only if GDB specifically asks for HR to be
excluded from a page titled "Team Overview."

---

## 10. Whether the proposed change is mandatory before D3

**No.** D3 (Payroll Cost Summary) is authorized by `payroll.read.all` — a flat permission with no
self/team ambiguity (Section 2) — and is blocked only by the already-documented missing
cost-total field on `PayrollRunDtos.Response` and the (now-resolved-by-this-review) question of
whether a new permission is needed, which it is not. **D3 can be implemented independently of
whether Part A/B above are adopted, in either order.** The only thing this review changes about
D3's path is confirming `report.payroll.read` is unnecessary (Section 3), closing that specific
open item from `docs/REPORTING_V1_REQUIREMENTS.md` Section G.

---

## 11. The smallest implementation plan

Ordered by what unblocks the most value per change:

1. **Employee Service**: add the `scope` field to `GET /employees`'s page metadata (Part A). One
   field, one service, no new permission yet. This alone closes Gap 1 completely for today's role
   model (HR uniquely holds `employee.read.all`) and narrows Gap 3 to a documented, accepted
   cosmetic exception.
2. **Expense Service**: add the same `scope` field to `GET /expenses/claims`'s page metadata
   (Part A). Closes Gap 2 completely for today's role model (Finance uniquely holds
   `expense.read.all`).
3. **Frontend**: update `workforce-summary/page.tsx` and `expense-summary/page.tsx` to gate on
   `page.scope === "ALL"`; add the optional `scope` field to `lib/api/types.ts`'s `PageMeta`.
4. *(Optional, do only if GDB wants Reporting fully decoupled from Employee/Expense's own `.all`
   permission rather than just detecting it)*: add `report.workforce.read` and
   `report.expense.read` (Part B) — RBAC.md catalogue + role rows, `SecurityConfig` matcher
   additions, `*AccessGuard` additional branches, as detailed in Sections 4–5.
5. **D3**, independently, whenever its own blockers (cost-total field, GDB's policy sign-off on
   exposing it) are resolved — not gated on steps 1–4 at all.

Steps 1–3 are the complete, "smallest production-safe" fix for both confirmed gaps. Step 4 is a
genuinely optional hardening layer the task explicitly asked this review to evaluate; it is
recommended but not required for the two gaps to be closed.

---

## Part A — implementation status: DONE

Steps 1–3 above are implemented. Step 4 (Part B) was explicitly not implemented, per instruction.

- **Employee Service**: `PageResponse.PageMeta` gained a `scope: ResponseScope` field
  (`SELF`/`TEAM`/`ALL`), set in `EmployeeService.list()` directly from
  `EmployeeAccessGuard.ListScope.unrestricted()` (never `SELF` in practice - this endpoint's guard
  has no self branch, confirmed in Section 1). `EmployeeAccessGuard` itself is unchanged.
- **Expense Service**: `PageResponse.PageMeta` gained the same `scope` field. Unlike Employee,
  `ExpenseAccessGuard.ListScope` needed a small, additive extension - a `Tier` enum (`SELF`/
  `TEAM`/`ALL`) recording which of the three branches in `resolveListScope` actually produced the
  scope, since `unrestricted`/`allowedIds` alone collapse self and team into the same
  indistinguishable shape. `restrictedTo(ids)` was split into `restrictedToTeam(ids)` and
  `restrictedToSelf(ids)` - both still constructed from exactly the same inputs the unmodified
  authorization decision already computed; no authorization *rule* changed.
- **Frontend**: `lib/api/types.ts`'s `PageMeta` gained an optional `scope` field.
  `workforce-summary/page.tsx` and `expense-summary/page.tsx` now fail closed on anything other
  than `scope === "ALL"` before rendering their report. `team-overview`/`leave-summary` were not
  touched (Section 9).

**Critical, pre-existing, unrelated bug discovered while writing the regression tests for Part A
— FIXED in a dedicated follow-up task** (scope: fix only these two confirmed PostgreSQL defects;
no authorization, business-behavior, or RBAC change). Both
`EmployeeRepository.searchAll`/`searchWithinScope`'s `:query` parameter and
`ExpenseClaimRepository.searchAll`'s `:from`/`:to` parameters had hit a Postgres
"could not determine data type of parameter" error in certain real-database parameter
combinations - confirmed via Testcontainers, not a mock artifact - affecting Workforce Summary,
Team Overview, and Leave Summary's `GET /employees` calls (none of which pass a `query` parameter)
and Expense Summary's `GET /expenses/claims` call (which sets `from`/`to` but never `status`).

- **Root cause (both services)**: in the idiomatic `(:param is null or ...)` optional-filter
  pattern, a parameter referenced *only* inside a standalone `is null` check gives PostgreSQL's
  extended query protocol no other syntactic context to infer a concrete type from. For Employee's
  `:query`, Postgres resolved it as `bytea`, and `lower()` has no `bytea` overload once that value
  reached `concat('%', :query, '%')` inside the `or` branch - raising
  `function lower(bytea) does not exist`. For Expense's `:from`/`:to`, the same untyped-parameter
  failure surfaced directly as `could not determine data type of parameter` in `searchAll`; after
  an initial fix attempt wrapped the parameters in `cast(:param as date)`, a *second*, different
  failure appeared specifically in `searchForEmployee`/`searchWithinScope` (both of which have an
  extra `employeeRef` equality/`IN` clause absent from `searchAll`): `cannot cast type bytea to
  date`, traced via Hibernate SQL/bind-parameter trace logging to Hibernate itself falling back to
  binding the cast-wrapped null parameter as untyped `JAVA_OBJECT` whenever an additional WHERE
  clause shares the statement.
- **Fix, Employee**: every occurrence of `:query` (and, defensively, `:status`), including the
  standalone `is null` check, is now wrapped in `cast(:param as string)` - a single, consistently-
  typed parameter regardless of null-ness.
- **Fix, Expense**: the standalone `is null` check was removed entirely, replaced with
  `coalesce(:param, <concrete sentinel>)` for all three repository methods -
  `coalesce(:from, cast('0001-01-01' as date))`, `coalesce(:to, cast('9999-12-31' as date))`, and a
  self-referential `coalesce(:status, c.status)` for the enum filter (an absent status filter then
  compares `c.status` to itself, always true). Every parameter occurrence is now always paired
  with a concretely-typed value, so Postgres never needs to resolve an unanchored type.
- **No change to filtering semantics, authorization, or RBAC**: no filter still means no filtering
  on that dimension in every method, in every case; `ListScope`/`EmployeeAccessGuard`/
  `ExpenseAccessGuard` and the `page.scope` field from Part A above are untouched.
- **Regression coverage, confirmed via real PostgreSQL/Testcontainers (not mocks)**:
  `EmployeeIntegrationTest` (9/9 passing) now covers `GET /employees` with no `query`, with a
  non-empty `query`, and empty-string `query` treated identically to no filter, under both ALL and
  TEAM scope. `ExpenseIntegrationTest` (16/16 passing) now covers `from`+`to` with no `status`
  (Expense Summary's exact pattern, including the empty-result case), no `from`/`to`, `status`
  alone, and all three filters together, under SELF/TEAM/ALL scope. Full `employee-service` and
  `expense-service` suites (every test class, not just these two) pass in full afterward.
- **Net effect**: all four implemented Reporting V1 pages' actual call patterns
  (`GET /employees` with no `query` for D1/D2/D5; `GET /expenses/claims` with `from`/`to` and no
  `status` for D4) are now confirmed to execute against real PostgreSQL without the previously-
  confirmed 500 error. No mandatory blocker remains from this defect.
