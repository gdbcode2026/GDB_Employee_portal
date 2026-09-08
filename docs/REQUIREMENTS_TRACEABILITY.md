# Requirements Traceability

This is an initial traceability baseline. “TBD” means the business rule, not a technical placeholder, remains to be approved.

| Business requirement | Module/service | API | Entity | Event | Permission | Test requirement |
|---|---|---|---|---|---|---|
| Employee dashboard/profile/directory | Employee, Organization | `GET /employees/me`, `/employees`, `/organization/chart` | Employee, Employment, Team, ReportingRelation | employee lifecycle | employee read/update self/team/all | Auth, field masking, scope, pagination, hierarchy-cycle tests |
| Attendance, WFH, regularization, holidays | Attendance, Workflow | attendance/WFH/regularization routes | AttendanceRecord, WfhRequest, RegularizationRequest, Holiday | attendance finalized/regularization approved | attendance/wfh permissions | timezone, duplicate/idempotency, approval and finalization tests |
| Leave management | Leave, Workflow | leave balance/request/decision routes | LeaveBalance, LeaveRequest | leave requested/approved/rejected | leave create/read/approve | balance concurrency, lifecycle, unauthorized decision tests |
| Payroll/payslips | Payroll* | payroll routes | PayrollRun, Payslip | payroll processed | payroll/payslip read/process | Deferred: policy, isolation, field masking, audit tests |
| Expenses/reimbursements | Expense, Document, Workflow | expense claim/decision/reimbursement routes | ExpenseClaim, ExpenseLine, ReceiptReference | expense submitted/approved | expense permissions | amount validation, receipt authorization, state and scope tests |
| Projects/tasks | Project | project/task routes | Project, Membership, Task | TBD | project/task permissions | membership and assignment authorization tests |
| Performance/goals | Performance | goal/review routes | Goal, ReviewCycle, PerformanceReview | TBD | performance permissions | reviewer scope, confidentiality, lifecycle tests |
| Documents/policies | Document | upload/download/policy routes | Document, Version, AccessGrant, Policy | document uploaded | document/policy permissions | type/size/scan/quarantine/access/audit tests |
| Assets | Asset, Workflow | asset/request/assignment routes | Asset, Assignment, Request | asset assigned | asset permissions | custody state, request approval, return/idempotency tests |
| Notifications/announcements | Notification | notification/preference routes | Preference, Template, Delivery | subscribed domain events | notification read/manage | preference, retry, no-domain-blocking tests |
| Onboarding/offboarding | Employee, Identity, Workflow, Asset | employee/workflow routes | Employment, WorkflowInstance, Assignment | employee create/deactivate, workflow completed | HR/admin scoped | lifecycle propagation, access revocation, audit tests |
| Audit/reporting | Audit, Reporting* | audit/report routes | AuditEntry, Projection | all domain events | audit/report permissions | append-only, redaction, projection lag/export scope tests |

