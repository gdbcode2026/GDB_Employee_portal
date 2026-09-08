# RBAC and Resource Authorization

The following matrix defines high-level intent, not final HR policy. Exact permissions must be converted into named capabilities and approved by GDB.

| Role | Baseline access |
|---|---|
| Employee | Own profile, requests, attendance, documents, expenses, assigned assets/tasks, permitted directory data |
| Team Lead | Employee access plus team task/project coordination and explicitly delegated approvals |
| Manager | Team-scoped people data, approvals, performance inputs, and reports permitted by policy |
| HR | Employee lifecycle, organization/leave data, approved HR documents and HR workflows |
| Finance | Expense reimbursement and payroll functions granted by policy |
| Admin | Operational configuration and non-super-admin administration |
| Super Admin | Break-glass/system administration, tightly audited and least-used |

## Rules

- Roles grant capabilities; they do not automatically grant every record in a domain.
- A request must satisfy both role/capability and resource scope: self, delegated team, organization scope, or explicit assignment.
- Manager/team scope is derived from Organization, not a client-provided employee ID.
- Finance, HR, and Admin access to payroll is explicit and audited. Employees can access only their own payslips.
- Deny by default. Permission changes, impersonation/break-glass actions, exports, and sensitive document downloads require enhanced audit logging.

## Permission catalogue and role mapping

Permission names are stable capabilities. Core vocabulary: `employee.read.self/team/all`, `employee.update.self/all`; `organization.read/manage`; `attendance.read.self/team/all`, `attendance.create.self`, `attendance.finalize.team`, `attendance.regularize.self/approve.team/approve.all`, `wfh.create.self/approve.team/approve.all`; `leave.read.self/team/all`, `leave.create.self`, `leave.cancel.self`, `leave.approve.team/all`; `payroll.read.self/all`, `payroll.process`, `payslip.read.self/all`; `expense.read.self/team/all`, `expense.create.self`, `expense.update.self`, `expense.submit.self`, `expense.approve.team/all`, `expense.reimburse`; `project.read/manage`, `task.manage.self/team`; `performance.read.self/team/all`, `performance.goal.manage.self`, `performance.review.submit.team`, `performance.manage`; `document.read.self/team/all`, `document.upload.self`, `document.manage`, `policy.publish`; `asset.read.self/all`, `asset.request.self`, `asset.manage`, `asset.assign`; `workflow.read.self/team/all`, `workflow.decide.assigned`, `workflow.manage`; `notification.read.self/manage`; `audit.read`; `report.read.self/team/all`, `report.export`; `identity.manage`, `role.manage`, `system.admin`, `breakglass.use`.

| Role | Granted permissions, always limited by resource rules |
|---|---|
| Employee | Own profile, attendance/WFH/leave/expense/document/asset request records; own payslip, notifications, goals and assigned tasks. |
| Team Lead | Employee permissions plus team read, task coordination, and only approval permissions when chosen by a configured workflow. |
| Manager | Employee permissions plus team employee/attendance/leave/expense/performance/report permissions and team approvals. |
| HR | Employee read/update all, organization management as approved, leave all/approval, HR document/policy/workflow/performance/report permissions. Payroll needs separately granted payroll permission. |
| Finance | Expense all/approval/reimbursement and payroll/payslip all/process plus finance reporting; no default HR-profile write. |
| Admin | Identity/role/operational configuration permissions; no automatic HR or payroll data access. |
| Super Admin | All permissions, including time-bound `breakglass.use`; dual control and enhanced audit required. |

`self` maps token subject to employee reference. `team` is confirmed from Organization's current reporting graph—not a caller-supplied team ID. `all` requires the named capability and a documented purpose for sensitive records. Workflow decisions require both task assignment/delegation validity and `workflow.decide.assigned`. Field masking/minimal response shapes apply to emergency contacts, payroll, reviews, receipts, document classifications, audit metadata, and exports.
