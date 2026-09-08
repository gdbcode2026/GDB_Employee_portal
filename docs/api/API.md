# Initial REST API Contract

All portal APIs are routed as `/api/v1`. Except OIDC discovery/login/callback, requests require a valid bearer token or approved BFF session. `201` denotes creation, `202` accepted asynchronous processing, `204` no content; errors are `400` validation, `401` unauthenticated, `403` unauthorized, `404` absent/not visible, `409` state/idempotency conflict, `422` business validation, `429` rate limit, and `5xx` server/dependency failure. Collection endpoints use `page`, `size`, `sort` and documented domain filters; responses are `{items, page:{number,size,total}}`. Exact field sets remain implementation contracts, not fake data.

| Module | Method / URL | Purpose, permission, body / response / validation |
|---|---|---|
| Auth | `GET /auth/authorize`, `POST /auth/token`, `POST /auth/revoke`, `GET /auth/userinfo` | OIDC standard endpoints; client authentication/PKCE where applicable. Responses follow OIDC/OAuth; validate registered client, redirect URI, grant and token state. |
| Employees | `GET /employees/me` | Own profile; `employee.read.self`; profile response with sensitive fields masked by policy. |
|  | `GET /employees/{id}`; `GET /employees` | Self/team/all lookup; `employee.read.self/team/all`; filters `departmentId,teamId,status,query`. Validate scope and pagination. |
|  | `PATCH /employees/me`; `POST /employees`; `PATCH /employees/{id}` | Self-approved fields or HR lifecycle management; `employee.update.self/all`; body contains only allowed profile/employment fields, validated email/date/status transition. |
| Organization | `GET /organization/departments`, `GET /organization/teams`, `GET /organization/chart` | Directory/hierarchy; `organization.read`; filters/status. No hidden employee PII. |
|  | `POST/PATCH /organization/departments/{id}`, `POST/PATCH /organization/teams/{id}` | Structure management; `organization.manage`; name/code/parent/status body; validate uniqueness/cycle-free hierarchy. |
| Attendance | `GET /attendance/me`, `GET /attendance` | Own/team/all attendance; `attendance.read.self/team/all`; filters `employeeId,from,to,status`. |
|  | `POST /attendance/check-ins`, `POST /attendance/check-outs` | Own time action; `attendance.create.self`; timestamp/location evidence only if policy supplies it; idempotency key, validate active day/shift. |
|  | `POST /attendance/wfh-requests`, `POST /attendance/regularizations` | Create own request; `wfh.create.self` / `attendance.regularize.self`; dates/reason body; validate range/state. |
|  | `POST /attendance/{id}/finalize` | Finalize permitted team record; `attendance.finalize.team`; body optional finalization reference; validate ownership/state. |
| Leave | `GET /leave/balances/me`, `GET /leave/requests` | Own/team/all; `leave.read.self/team/all`; filters employee/status/date/type. |
|  | `POST /leave/requests`; `POST /leave/requests/{id}/cancel` | Create/cancel own leave; `leave.create.self` / `leave.cancel.self`; type/start/end/reason body; validate balance, date range and lifecycle. |
|  | `POST /leave/requests/{id}/decisions` | Direct permitted decision only where configured; `leave.approve.team/all`; decision/comment; validate workflow/task/state. |
| Payroll* | `GET /payroll/payslips/me`, `GET /payroll/payslips/{id}`, `GET /payroll/runs` | Own/authorized payroll access; `payroll.read.self/all`; filters period/employee; response never exposes unauthorized pay data. |
|  | `POST /payroll/runs` | Start authorized process; `payroll.process`; period/body is undefined until payroll requirements approved; reject until then. |
| Expenses | `GET /expenses/claims`, `GET /expenses/claims/{id}` | Own/team/all claims; `expense.read.self/team/all`; filters status/date/employee. |
|  | `POST /expenses/claims`, `PATCH /expenses/claims/{id}`, `POST /expenses/claims/{id}/submit` | Create/update/submit own draft; `expense.create.self/update.self/submit.self`; lines/currency/receipt refs; validate currency, positive amounts, draft state, idempotency. |
|  | `POST /expenses/claims/{id}/decisions`, `POST /expenses/claims/{id}/reimbursements` | Approve or mark reimbursement; `expense.approve.team/all` / `expense.reimburse`; validate workflow and terminal states. |
| Projects | `GET/POST /projects`, `GET/PATCH /projects/{id}` | Read/manage per membership; `project.read/manage`; project body code/name/status. |
|  | `GET/POST /projects/{id}/tasks`, `PATCH /tasks/{id}` | Task read/manage self/team; task title/assignee/status/due date; validate membership and state. |
| Performance | `GET/POST /performance/goals`, `PATCH /performance/goals/{id}` | Own goals; `performance.goal.manage.self`; validate ownership/status/date. |
|  | `GET /performance/reviews`, `POST /performance/reviews/{id}/submit` | Scoped review access/submission; `performance.read.self/team/all`, `performance.review.submit.team`; validate cycle/reviewer/visibility. |
| Documents | `POST /documents/uploads`, `POST /documents/uploads/{id}/complete` | Start/complete own permitted upload; `document.upload.self`; metadata/scan completion only; validate type/size/checksum and quarantine state. |
|  | `GET /documents/{id}`, `GET /documents/{id}/download`, `GET /policies` | Authorized metadata/download/policies; `document.read.self/team/all`; access check, scan status, short-lived download result. |
|  | `POST /policies`, `PATCH /policies/{id}` | Publish/manage policy; `policy.publish`; document reference/status body. |
| Assets | `GET /assets/me`, `GET /assets`, `POST /asset-requests` | Own inventory / all / request; `asset.read.self/all`, `asset.request.self`; filter/status or request type/justification. |
|  | `POST /assets`, `POST /assets/{id}/assignments`, `POST /assignments/{id}/return` | Inventory/custody; `asset.manage/assign`; validate asset state and idempotency. |
| Workflows | `GET /workflows/tasks/me`, `GET /workflows/{id}` | Own assigned/scoped tasks; `workflow.read.self/team/all`. |
|  | `POST /workflows/{id}/tasks/{taskId}/decisions`, `POST /workflows/{id}/cancel` | Decision/cancel; `workflow.decide.assigned`; decision/comment; validate active assignment/delegation/terminal state. |
| Notifications | `GET /notifications`, `PATCH /notification-preferences/me` | Own messages/preferences; `notification.read.self`; pagination, channel/enabled body. |
| Audit | `GET /audit/events` | Restricted search; `audit.read`; filters actor/resource/action/time/correlation, paginated and redacted. |
| Reporting* | `GET /reports`, `POST /reports/{id}/runs` | Scoped report/run; `report.read.self/team/all`, `report.export`; filters/report parameters validated against approved definition. |

`*` endpoints are documented contracts only and remain unavailable until their deferred domains are approved. Service-to-service endpoints use separate internal routes/audiences and are not browser-public.

