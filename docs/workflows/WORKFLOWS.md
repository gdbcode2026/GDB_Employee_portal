# Key Workflows

## Employee onboarding

HR creates/updates the employee record. Employee publishes the committed lifecycle event. Consumers provision directory projections, onboarding tasks/workflows, notifications, and audit entries. Account provisioning is an explicit Identity/Auth operation or an integration event once the identity-provider approach is chosen.

## Leave request

Employee submits to Leave. Leave validates eligibility from its own balance/policy data, creates a request, and emits `LEAVE_APPLIED`. Workflow creates approval work where required; the decision is returned to Leave through an authorized API or command/event contract. Leave records the final decision and publishes `LEAVE_APPROVED` or `LEAVE_REJECTED`; Notification informs relevant parties.

## Attendance regularization and WFH

Attendance owns both request records and the final attendance effect. It may use Workflow for approval. It emits a finalization event only after the attendance record is committed.

## Expense reimbursement

Expense owns the claim and receipt metadata. Document/object storage secures uploaded receipts. Expense publishes `EXPENSE_SUBMITTED`; Workflow coordinates approval. After approval, Finance handling and the eventual reimbursement state remain Expense-owned unless a future finance integration creates a justified boundary.

## Payroll and payslips

Payroll consumes approved/finalized input events or fetches authorized period data through explicit APIs, then records a payroll run and emits `PAYROLL_PROCESSED`. Payroll calculations, tax, payment execution, and statutory reporting await jurisdiction/provider requirements. Payslip access is self-only unless a narrowly authorized role is approved.

## Offboarding

HR initiates an offboarding workflow. Workflow coordinates required tasks; Asset confirms return, Document handles retention instructions, Identity/Auth revokes access at the approved termination point, and Audit records all actions. Final rules need HR/legal approval.

## Reusable approval workflow model

Workflow owns configurable definitions, instances and approval tasks; the request domain remains authoritative for its request and applies only a verified terminal outcome. Supported request types are Leave, WFH, Attendance Regularization, Expense, Asset Request, and Document Request. A definition selects requester eligibility, ordered approval stages, approver resolver, optional multi-level rules, SLA, delegation eligibility, escalation target, cancellation point, required comments, and notification templates. These are configuration inputs for GDB—not assumed company policy.

An employee submits to the owning domain, which persists `SUBMITTED` and emits/starts a workflow. Workflow resolves approvers from configured role, manager chain, named group, or explicit assignment and creates one or more tasks. A stage may require one or all configured approvers. Decision is approve/reject; rejection ends the instance, with recorded comment if the definition requires it. Requester cancellation is permitted only while configured as cancellable. A valid delegation transfers task authority for a bounded period and retains both delegator and delegate in audit history. SLA breach creates an escalation task/notification according to the definition; it does not auto-approve unless GDB expressly configures that behavior.

Every submission, assignment, delegation, decision, cancellation, escalation, delivery trigger, and terminal outcome creates audit data with correlation ID. Notification subscribes to lifecycle events. Duplicate start/decision events are idempotent; a terminal `WORKFLOW_COMPLETED` event is consumed by the owning domain, which validates subject/state before applying the result.
