# Payroll and Reporting — Business Decision Document

This document collects the business decisions GDB must make before Payroll Service or
Reporting Service can be implemented. It does not answer any of these questions. Every status
below is drawn only from what `docs/DEVELOPMENT_ROADMAP.md`, `docs/architecture/MICROSERVICES.md`,
`docs/database/DATABASE.md`, `docs/api/API.md`, `docs/security/RBAC.md`,
`docs/architecture/COMMUNICATION.md`, `docs/ARCHITECTURE_REVIEW.md`, and `docs/DECISIONS.md`
already state. Where an example answer set is listed, it is an illustration of the shape of
the decision (e.g. common pay frequencies), never a recommendation or a default.

See also: `docs/DEVELOPMENT_ROADMAP.md` Phase 5, `docs/ARCHITECTURE_REVIEW.md` decision #4, and
`docs/DECISIONS.md`'s "Payroll geography and provider integration" open decision, which this
document expands into a complete checklist.

## Section 1 — Payroll Decisions

### 1. Payroll geography/jurisdiction
- **Decision required:** Which country/countries and legal jurisdictions does GDB need payroll for?
- **Why it matters:** Determines applicable labor law, statutory tax regime, currency, and which providers are even eligible; every other payroll decision depends on this one.
- **Current documentation status:** NOT DOCUMENTED. Named as an unresolved item in `ARCHITECTURE_REVIEW.md` decision #4 and `DECISIONS.md`'s "Payroll geography and provider integration."
- **Example answer options:** N/A — jurisdiction is a GDB business fact, not a design choice.

### 2. Payroll frequency and period definition
- **Decision required:** What pay cycle(s) apply, and what fields does a PayrollPeriod need (start/end dates, status, cut-off rules)?
- **Why it matters:** `PayrollRun` references a `period_id FK` (`DATABASE.md`), but `PayrollPeriod` itself has zero documented fields.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options (illustrative, non-prescriptive):** monthly / biweekly / semi-monthly / weekly.

### 3. Salary/compensation structure
- **Decision required:** How is an employee's base compensation represented (annual salary, hourly rate, grade/band)?
- **Why it matters:** `PayComponent` has only `code/type/amount` documented — nothing links it to how a baseline salary is established.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 4. Pay components
- **Decision required:** What are the actual PayComponent codes/types GDB uses (e.g. base pay, overtime, bonus, allowance)?
- **Why it matters:** `DATABASE.md` defines the column shape only (`code/type/amount`), not the vocabulary populating it.
- **Current documentation status:** NOT DOCUMENTED (structural shape only).
- **Example answer options:** N/A — this is GDB's own compensation taxonomy.

### 5. Employee-to-compensation assignment
- **Decision required:** How and by whom is an employee assigned their pay components/rate, and does an assignment change need to be effective-dated?
- **Why it matters:** No entity or endpoint documents this assignment anywhere in the current contract set.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 6. Deductions
- **Decision required:** What deduction types exist (statutory, voluntary, loan repayment, etc.) and how is each calculated?
- **Why it matters:** No deduction concept exists anywhere in the documented Payroll model.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A — jurisdiction/policy-specific.

### 7. Benefits/withholdings
- **Decision required:** Does GDB provide benefits (health, retirement, etc.) that must be withheld or matched through payroll?
- **Why it matters:** Same gap as deductions; not mentioned anywhere.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 8. Tax/statutory rules
- **Decision required:** What tax withholding and statutory filing rules apply, and who supplies and maintains them?
- **Why it matters:** Incorrect tax handling is a legal/compliance risk, not just a technical one.
- **Current documentation status:** BLOCKED — explicitly named as a non-negotiable prerequisite in three separate documents: `MICROSERVICES.md` ("No unsupplied tax rules"), `ARCHITECTURE_REVIEW.md` ("payroll legal/tax/provider requirements"), and `DECISIONS.md` ("Do not decide payroll, statutory tax... without accountable business/legal input").
- **Example answer options:** N/A — must come from GDB Finance/Legal, never invented.

### 9. Currency
- **Decision required:** What currency(ies) does payroll operate in? Is multi-currency support required?
- **Why it matters:** No currency field or conversion policy is documented for PayComponent/Payslip, unlike Expense Service's explicit currency field.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 10. Payroll calculation ownership
- **Decision required:** Does GDB's own Payroll Service calculate pay, or does an external provider calculate it while GDB only records/stores results?
- **Why it matters:** Changes the service's entire responsibility boundary and API shape.
- **Current documentation status:** NOT DOCUMENTED. `WORKFLOWS.md` says Payroll "consumes approved/finalized input events or fetches authorized period data... then records a payroll run" — ambiguous about where calculation actually happens.
- **Example answer options (illustrative):** in-house calculation / third-party payroll provider calculates, GDB records / hybrid.

### 11. Payroll approval
- **Decision required:** Does a payroll run require approval before finalization, and by whom?
- **Why it matters:** No approval endpoint, permission, or Workflow integration exists for Payroll — it is absent from `WORKFLOWS.md`'s six supported Workflow request types and from `WORKFLOW_COMPLETED`'s documented consumer list.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 12. Payroll correction/reprocessing
- **Decision required:** How is an error in a finalized payroll run corrected — reversal, adjustment run, or manual correction?
- **Why it matters:** No correction or reprocessing mechanism is documented anywhere.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 13. Payroll finalization
- **Decision required:** What does "finalized" mean for a PayrollRun, and is a finalized run reversible?
- **Why it matters:** No PayrollRun status/lifecycle is documented anywhere — unique among all entities in `DATABASE.md`, which enumerates every other entity's states explicitly.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 14. Payslip contents
- **Decision required:** What fields/line items must appear on a payslip (gross, net, per-component breakdown, deductions, year-to-date totals)?
- **Why it matters:** The Payslip entity has only a `storage ref S` documented — no content field list at all.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 15. Payslip storage
- **Decision required:** Where/how is the payslip document itself stored — does it reuse Document Service, or a Payroll-owned object-storage reference?
- **Why it matters:** `DATABASE.md`'s "storage ref `S`" implies an external reference but does not say which system owns it.
- **Current documentation status:** AMBIGUOUS — no cross-service ownership is stated.
- **Example answer options (illustrative):** Document Service reference / Payroll-owned object storage reference.

### 16. Payroll provider/payment integration
- **Decision required:** Which external payroll or payment provider (if any) will GDB integrate with?
- **Why it matters:** Explicitly named as an unselected external dependency blocking implementation.
- **Current documentation status:** BLOCKED — `ARCHITECTURE_REVIEW.md` ("external dependencies not yet selected"), decision #4, and `DECISIONS.md` ("Payroll geography and provider integration").
- **Example answer options:** N/A — vendor selection is a GDB business decision.

### 17. HR vs Finance access
- **Decision required:** Which of HR and Finance can read/process payroll, and does one require the other's approval?
- **Why it matters:** `RBAC.md` states HR "needs separately granted payroll permission" and Finance gets "payroll/payslip all/process," but the interaction between the two roles for payroll specifically is not defined.
- **Current documentation status:** DOCUMENTED (partial) — permission names (`payroll.read.all`, `payroll.process`) exist; the HR/Finance interaction rule does not.
- **Example answer options:** N/A.

### 18. Employee access
- **Decision required:** Can an employee only view their own payslip, or also request corrections or download history?
- **Why it matters:** The self-view baseline is documented, but any self-service action beyond viewing is not.
- **Current documentation status:** DOCUMENTED (view-only baseline) — `RBAC.md`: "Employees can access only their own payslips"; `WORKFLOWS.md`: "Payslip access is self-only unless a narrowly authorized role is approved." NOT DOCUMENTED beyond that.
- **Example answer options:** N/A.

### 19. Payroll data retention
- **Decision required:** How long must payroll/payslip records be retained, and under what legal basis?
- **Why it matters:** Highly sensitive financial/PII data; incorrect retention is a compliance risk.
- **Current documentation status:** NOT DOCUMENTED. `ARCHITECTURE_REVIEW.md`'s "Missing requirements" names retention generally, with no payroll-specific period.
- **Example answer options:** N/A — jurisdiction/legal-specific.

### 20. Sensitive-data controls
- **Decision required:** What specific field masking, encryption, and access-logging controls apply to payroll data beyond the platform baseline?
- **Why it matters:** `DATABASE.md` states "All values/access/exports highly sensitive and audited" and `MICROSERVICES.md` requires "Separate routes/credentials/logging; fail closed" — the principle is documented, but concrete controls (e.g. field-level encryption, dual-approval exports) are not.
- **Current documentation status:** DOCUMENTED (principle only).
- **Example answer options:** N/A.

### 21. Required payroll events
- **Decision required:** Is `PAYROLL_PROCESSED` (run/period ID, employee count) sufficient, or are additional events needed (e.g. a payslip-generated or payroll-corrected event)?
- **Why it matters:** Only one event is currently documented; downstream consumers (Notification, Audit, Reporting) may need finer-grained signals.
- **Current documentation status:** DOCUMENTED (one event) — `COMMUNICATION.md`.
- **Example answer options:** N/A.

### 22. Leave/attendance inputs
- **Decision required:** Exactly which Leave/Attendance data feeds a payroll run, and how are `LEAVE_APPROVED`/`ATTENDANCE_FINALIZED` events (or "authorized period data through explicit APIs") consumed and reconciled?
- **Why it matters:** `WORKFLOWS.md` is worded as an either/or ("consumes... events or fetches... through explicit APIs") without specifying which, or the reconciliation logic.
- **Current documentation status:** AMBIGUOUS — the inputs are named in `COMMUNICATION.md` (both flagged `Payroll*`, a deferred consumer), the mechanism is not.
- **Example answer options:** N/A.

### 23. Payroll reporting requirements
- **Decision required:** What payroll-specific data must flow into Reporting, and under what access restriction?
- **Why it matters:** Cross-cutting with Reporting Section 2, Q15; payroll is the most sensitive dataset in the platform.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

## Section 2 — Reporting Decisions

### 1. Required reports
- **Decision required:** What specific reports does GDB actually need? (List them.)
- **Why it matters:** No report is named anywhere in any document; `ReportDefinition` exists only as a generic entity shape with no seeded content.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 2. Report name/purpose
- **Decision required:** For each required report, what is its name and business purpose?
- **Why it matters:** Cannot design `ReportDefinition`'s "query spec" (`DATABASE.md`) without knowing what is being asked for.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 3. Intended users
- **Decision required:** Who consumes each report — Employee, Manager, HR, Finance, Admin?
- **Why it matters:** Determines how `report.read.self/team/all` applies per report.
- **Current documentation status:** NOT DOCUMENTED (permission names exist generically; per-report audience does not).
- **Example answer options:** N/A.

### 4. Self/team/all access
- **Decision required:** Does every report support all three scopes, or do some reports (e.g. anything touching payroll) restrict to `all`-only for Finance?
- **Why it matters:** `RBAC.md` defines the generic `report.read.self/team/all` pattern but not per-report applicability.
- **Current documentation status:** AMBIGUOUS.
- **Example answer options:** N/A.

### 5. Data sources
- **Decision required:** Which specific domain events/services feed which specific projection?
- **Why it matters:** `MICROSERVICES.md` only says Reporting "consumes approved events" generically; no named event-to-projection mapping exists.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 6. Filters
- **Decision required:** What filter parameters does each report accept (date range, department, employee, status)?
- **Why it matters:** `API.md` says "filters/report parameters validated against approved definition" without defining any parameter.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 7. Date ranges
- **Decision required:** Are reports always date-bounded? What is the maximum allowed range?
- **Why it matters:** Affects projection query cost and export size; not addressed anywhere.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 8. Aggregations
- **Decision required:** Do reports aggregate (sums, counts, averages) or return raw record sets?
- **Why it matters:** "Read-optimized cross-domain reports" (`MICROSERVICES.md`) implies some aggregation but does not specify which.
- **Current documentation status:** AMBIGUOUS.
- **Example answer options:** N/A.

### 9. Real-time vs generated reports
- **Decision required:** Are reports computed on demand from live projections, or pre-generated on a schedule?
- **Why it matters:** Determines the entire request/response shape and infrastructure cost.
- **Current documentation status:** AMBIGUOUS — `ReportRun`'s "status/output ref" fields (`DATABASE.md`) imply an asynchronous generate-then-fetch model, but this is inferred from the entity's shape, not stated in prose anywhere.
- **Example answer options (illustrative):** on-demand generation / scheduled pre-generation / hybrid.

### 10. ReportRun behavior
- **Decision required:** What does a ReportRun's lifecycle look like (states, output expiry, re-run behavior)?
- **Why it matters:** `DATABASE.md` names the fields ("definition FK/requester/status/output ref") but no status enum or lifecycle is documented — the same gap pattern as PayrollRun.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 11. Export formats
- **Decision required:** What export format(s) are required (CSV, XLSX, PDF)?
- **Why it matters:** The `report.export` permission exists, but its output format does not.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options (illustrative, industry-standard):** CSV / XLSX / PDF.

### 12. Scheduled reports
- **Decision required:** Can a report be scheduled to run/deliver automatically (e.g. weekly to a manager)?
- **Why it matters:** No schedule field exists on ReportDefinition or ReportRun today.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 13. Dashboards
- **Decision required:** Is a dashboard/visual UI in scope, or only downloadable report output?
- **Why it matters:** `MICROSERVICES.md` scopes Reporting to "reports," not dashboards; no dashboard concept is named anywhere.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 14. Analytics scope
- **Decision required:** Is predictive/trend analytics in scope, or only descriptive historical reporting?
- **Why it matters:** `MICROSERVICES.md`'s boundary rule ("Reporting is read-only and never becomes a transactional source of truth") bounds it to reporting, not modeling, but does not rule analytics in or out.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 15. Payroll reporting
- **Decision required:** What payroll data (if any) is exposed through Reporting, and to whom?
- **Why it matters:** Cross-references Payroll Section 1, Q23; payroll is the platform's most sensitive dataset, and both domains are deferred together.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 16. Expense reporting
- **Decision required:** What expense analytics/reports are needed (e.g. spend by category or department)?
- **Why it matters:** Expense Service now exists and publishes events Reporting could consume, but no specific expense report is named.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 17. Attendance/leave reporting
- **Decision required:** What attendance/leave reports are needed (e.g. absence trends, leave balance summaries)?
- **Why it matters:** Same gap as Expense — the source services exist, but no report is named.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 18. Performance reporting
- **Decision required:** What performance/goal reports are needed, and how does review confidentiality carry into a report context?
- **Why it matters:** `DATABASE.md` states performance reviews are "restricted/audited" — a report could leak restricted review content if scoping is not deliberate.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 19. Project reporting
- **Decision required:** What project/task reports are needed (e.g. utilization, task completion)?
- **Why it matters:** Same gap as Expense/Attendance — no report is named.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 20. Data retention
- **Decision required:** How long are Projection and ReportRun output artifacts retained?
- **Why it matters:** Projections are event-fed copies of source data and may carry the same sensitivity as their origin; unbounded retention duplicates that risk.
- **Current documentation status:** NOT DOCUMENTED.
- **Example answer options:** N/A.

### 21. Export security
- **Decision required:** Do exports require additional approval, watermarking, or expiring links?
- **Why it matters:** `RBAC.md` names "exports" among actions requiring enhanced audit logging, but no concrete export-security control is specified.
- **Current documentation status:** DOCUMENTED (principle only) — `RBAC.md`: "Permission changes, impersonation/break-glass actions, exports, and sensitive document downloads require enhanced audit logging."
- **Example answer options:** N/A.

### 22. Sensitive-data masking
- **Decision required:** Which fields must be masked or excluded from report output by default (e.g. payroll amounts, review comments, emergency contacts)?
- **Why it matters:** `RBAC.md` names the general categories requiring masking ("payroll, reviews, receipts, document classifications, audit metadata, and exports") but not per-report masking rules.
- **Current documentation status:** DOCUMENTED (principle only).
- **Example answer options:** N/A.

## Section 3 — Explicit Current Constraints

- Payroll Service is deferred. It does not exist in the codebase and must not be implemented until the decisions in Section 1 are resolved.
- Reporting Service is deferred. It does not exist in the codebase and must not be implemented until the decisions in Section 2 are resolved.
- No implementation, migration, API, or permission for either domain should begin until the relevant decisions below are marked Approved.
- No tax, statutory, or payroll calculation rule may be invented by engineering. These must come from GDB Finance/Legal.
- No report definition, metric, or export format may be invented by engineering. These must come from GDB business stakeholders.

## Section 4 — Approval Checklist

For each decision, mark exactly one box, then record who decided and when.

### Payroll

| # | Decision | Approved | Rejected | Needs Clarification | Owner / Approver | Decision Date |
|---|---|---|---|---|---|---|
| P1 | Payroll geography/jurisdiction | [ ] | [ ] | [ ] | | |
| P2 | Payroll frequency and period definition | [ ] | [ ] | [ ] | | |
| P3 | Salary/compensation structure | [ ] | [ ] | [ ] | | |
| P4 | Pay components | [ ] | [ ] | [ ] | | |
| P5 | Employee-to-compensation assignment | [ ] | [ ] | [ ] | | |
| P6 | Deductions | [ ] | [ ] | [ ] | | |
| P7 | Benefits/withholdings | [ ] | [ ] | [ ] | | |
| P8 | Tax/statutory rules | [ ] | [ ] | [ ] | | |
| P9 | Currency | [ ] | [ ] | [ ] | | |
| P10 | Payroll calculation ownership | [ ] | [ ] | [ ] | | |
| P11 | Payroll approval | [ ] | [ ] | [ ] | | |
| P12 | Payroll correction/reprocessing | [ ] | [ ] | [ ] | | |
| P13 | Payroll finalization | [ ] | [ ] | [ ] | | |
| P14 | Payslip contents | [ ] | [ ] | [ ] | | |
| P15 | Payslip storage | [ ] | [ ] | [ ] | | |
| P16 | Payroll provider/payment integration | [ ] | [ ] | [ ] | | |
| P17 | HR vs Finance access | [ ] | [ ] | [ ] | | |
| P18 | Employee access | [ ] | [ ] | [ ] | | |
| P19 | Payroll data retention | [ ] | [ ] | [ ] | | |
| P20 | Sensitive-data controls | [ ] | [ ] | [ ] | | |
| P21 | Required payroll events | [ ] | [ ] | [ ] | | |
| P22 | Leave/attendance inputs | [ ] | [ ] | [ ] | | |
| P23 | Payroll reporting requirements | [ ] | [ ] | [ ] | | |

### Reporting

| # | Decision | Approved | Rejected | Needs Clarification | Owner / Approver | Decision Date |
|---|---|---|---|---|---|---|
| R1 | Required reports | [ ] | [ ] | [ ] | | |
| R2 | Report name/purpose | [ ] | [ ] | [ ] | | |
| R3 | Intended users | [ ] | [ ] | [ ] | | |
| R4 | Self/team/all access | [ ] | [ ] | [ ] | | |
| R5 | Data sources | [ ] | [ ] | [ ] | | |
| R6 | Filters | [ ] | [ ] | [ ] | | |
| R7 | Date ranges | [ ] | [ ] | [ ] | | |
| R8 | Aggregations | [ ] | [ ] | [ ] | | |
| R9 | Real-time vs generated reports | [ ] | [ ] | [ ] | | |
| R10 | ReportRun behavior | [ ] | [ ] | [ ] | | |
| R11 | Export formats | [ ] | [ ] | [ ] | | |
| R12 | Scheduled reports | [ ] | [ ] | [ ] | | |
| R13 | Dashboards | [ ] | [ ] | [ ] | | |
| R14 | Analytics scope | [ ] | [ ] | [ ] | | |
| R15 | Payroll reporting | [ ] | [ ] | [ ] | | |
| R16 | Expense reporting | [ ] | [ ] | [ ] | | |
| R17 | Attendance/leave reporting | [ ] | [ ] | [ ] | | |
| R18 | Performance reporting | [ ] | [ ] | [ ] | | |
| R19 | Project reporting | [ ] | [ ] | [ ] | | |
| R20 | Data retention | [ ] | [ ] | [ ] | | |
| R21 | Export security | [ ] | [ ] | [ ] | | |
| R22 | Sensitive-data masking | [ ] | [ ] | [ ] | | |
