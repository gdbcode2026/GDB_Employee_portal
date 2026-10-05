package com.growdigitalbridge.payroll.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emits a structured, correlation-tagged log line for every audit-worthy payroll action
 * (PAYROLL_REQUIREMENTS.md Section R): period creation, run creation/processing/approval/
 * rejection/finalization (including a rejected self-approval attempt), and sensitive payroll
 * reads. Every method signature deliberately excludes any monetary field - there is no
 * parameter through which a salary/tax value could reach this logger (decision 14: "never write
 * salary/tax values into ordinary application logs"). Log lines are captured by this platform's
 * existing structured console format (see application.yml's {@code logging.structured.format},
 * used identically by every other service) rather than a dedicated per-transition domain event -
 * COMMUNICATION.md documents only PAYROLL_PROCESSED/PAYSLIP_GENERATED for Payroll (see
 * PayrollRunService for where those are produced).
 */
@Component
public class PayrollAuditLog {

    private static final Logger log = LoggerFactory.getLogger("PAYROLL_AUDIT");

    public void periodCreated(UUID periodId, String actor, UUID correlationId) {
        record("period.created", periodId, actor, correlationId, null);
    }

    public void runCreated(UUID runId, String actor, UUID correlationId) {
        record("run.created", runId, actor, correlationId, null);
    }

    public void adjustmentRunCreated(UUID adjustmentRunId, UUID correctsRunId, String actor, UUID correlationId) {
        log.info("action=run.adjustment_created runId={} correctsRunId={} actor={} correlationId={}",
                adjustmentRunId, correctsRunId, actor, correlationId);
    }

    public void processingStarted(UUID runId, String actor, UUID correlationId) {
        record("run.processing_started", runId, actor, correlationId, null);
    }

    public void calculationCompleted(UUID runId, String actor, UUID correlationId, int lineCount, int exceptionCount) {
        record("run.calculation_completed", runId, actor, correlationId,
                "lineCount=" + lineCount + " exceptionCount=" + exceptionCount);
    }

    /** {@code failureType}/{@code failureMessage} are the failing exception's class name and message only - safe metadata, never a payroll value. */
    public void calculationFailed(UUID runId, String actor, UUID correlationId, String failureType, String failureMessage) {
        log.warn("action=run.calculation_failed runId={} actor={} correlationId={} failureType={} failureMessage={}",
                runId, actor, correlationId, failureType, failureMessage);
    }

    public void payrollException(UUID runId, UUID employeeRef, String reasonCode, String actor, UUID correlationId) {
        log.info("action=run.payroll_exception runId={} employeeRef={} reason={} actor={} correlationId={}",
                runId, employeeRef, reasonCode, actor, correlationId);
    }

    public void runSubmittedForApproval(UUID runId, String actor, UUID correlationId) {
        record("run.submitted_for_approval", runId, actor, correlationId, null);
    }

    public void runApproved(UUID runId, String actor, UUID correlationId) {
        record("run.approved", runId, actor, correlationId, null);
    }

    public void runRejected(UUID runId, String actor, UUID correlationId) {
        record("run.rejected", runId, actor, correlationId, null);
    }

    public void runFinalized(UUID runId, String actor, UUID correlationId) {
        record("run.finalized", runId, actor, correlationId, null);
    }

    public void selfApprovalRejected(UUID runId, String actor, UUID correlationId) {
        record("run.self_approval_denied", runId, actor, correlationId, "attempted approve/reject/finalize on a self-initiated run");
    }

    public void sensitiveRead(String resourceType, UUID resourceId, String actor, UUID correlationId) {
        log.info("action={} resourceId={} actor={} correlationId={}", resourceType + ".read", resourceId, actor, correlationId);
    }

    public void payslipGenerated(UUID payslipId, UUID employeeRef, UUID runId, String actor, UUID correlationId) {
        log.info("action=payslip.generated payslipId={} employeeRef={} runId={} actor={} correlationId={}",
                payslipId, employeeRef, runId, actor, correlationId);
    }

    /** {@code failureType} is the failing exception's class name only - safe metadata, never a payroll value. */
    public void payslipGenerationFailed(UUID runId, UUID employeeRef, String failureType, String actor, UUID correlationId) {
        log.warn("action=payslip.generation_failed runId={} employeeRef={} failureType={} actor={} correlationId={}",
                runId, employeeRef, failureType, actor, correlationId);
    }

    /** {@code viewerRole} distinguishes an employee viewing their own payslip from HR/Finance viewing another's (item 10: enhanced audit for HR/Finance). */
    public void payslipViewed(UUID payslipId, UUID employeeRef, String viewerRole, String actor, UUID correlationId) {
        log.info("action=payslip.viewed payslipId={} employeeRef={} viewerRole={} actor={} correlationId={}",
                payslipId, employeeRef, viewerRole, actor, correlationId);
    }

    public void payslipDownloaded(UUID payslipId, UUID employeeRef, String viewerRole, String actor, UUID correlationId) {
        log.info("action=payslip.downloaded payslipId={} employeeRef={} viewerRole={} actor={} correlationId={}",
                payslipId, employeeRef, viewerRole, actor, correlationId);
    }

    /** No amount/field value is ever a parameter here - only IDs (decision 14). */
    public void compensationCreated(UUID compensationId, UUID employeeRef, String actor, UUID correlationId) {
        log.info("action=compensation.created compensationId={} employeeRef={} actor={} correlationId={}",
                compensationId, employeeRef, actor, correlationId);
    }

    public void compensationUpdated(UUID compensationId, UUID employeeRef, String actor, UUID correlationId) {
        log.info("action=compensation.updated compensationId={} employeeRef={} actor={} correlationId={}",
                compensationId, employeeRef, actor, correlationId);
    }

    public void payComponentCreated(UUID payComponentId, String actor, UUID correlationId) {
        log.info("action=pay_component.created payComponentId={} actor={} correlationId={}", payComponentId, actor, correlationId);
    }

    public void payComponentUpdated(UUID payComponentId, String actor, UUID correlationId) {
        log.info("action=pay_component.updated payComponentId={} actor={} correlationId={}", payComponentId, actor, correlationId);
    }

    /** No UAN/PF/ESI identifier value is ever a parameter here - only IDs (decision 14). */
    public void statutoryProfileCreated(UUID profileId, UUID employeeRef, String actor, UUID correlationId) {
        log.info("action=statutory_profile.created profileId={} employeeRef={} actor={} correlationId={}",
                profileId, employeeRef, actor, correlationId);
    }

    public void statutoryProfileUpdated(UUID profileId, UUID employeeRef, String actor, UUID correlationId) {
        log.info("action=statutory_profile.updated profileId={} employeeRef={} actor={} correlationId={}",
                profileId, employeeRef, actor, correlationId);
    }

    public void payrollExceptionResolved(UUID exceptionId, UUID runId, UUID employeeRef, String actor, UUID correlationId) {
        log.info("action=run.payroll_exception_resolved exceptionId={} runId={} employeeRef={} actor={} correlationId={}",
                exceptionId, runId, employeeRef, actor, correlationId);
    }

    /** {@code code} is a rule-family identifier (e.g. "PF_EMPLOYEE_CONTRIBUTION"), never an amount/percentage/identifier value. */
    public void statutoryRuleCreated(UUID ruleId, String code, int ruleVersion, String actor, UUID correlationId) {
        log.info("action=statutory_rule.created ruleId={} code={} ruleVersion={} actor={} correlationId={}",
                ruleId, code, ruleVersion, actor, correlationId);
    }

    public void statutoryRuleUpdated(UUID ruleId, String actor, UUID correlationId) {
        log.info("action=statutory_rule.updated ruleId={} actor={} correlationId={}", ruleId, actor, correlationId);
    }

    public void statutoryRuleActivated(UUID ruleId, String code, int ruleVersion, String actor, UUID correlationId) {
        log.info("action=statutory_rule.activated ruleId={} code={} ruleVersion={} actor={} correlationId={}",
                ruleId, code, ruleVersion, actor, correlationId);
    }

    public void statutoryRuleDeactivated(UUID ruleId, String code, int ruleVersion, String actor, UUID correlationId) {
        log.info("action=statutory_rule.deactivated ruleId={} code={} ruleVersion={} actor={} correlationId={}",
                ruleId, code, ruleVersion, actor, correlationId);
    }

    private void record(String action, UUID resourceId, String actor, UUID correlationId, String note) {
        log.info("action={} resourceId={} actor={} correlationId={} note={}", action, resourceId, actor, correlationId, note);
    }
}
