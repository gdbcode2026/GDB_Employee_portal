package com.growdigitalbridge.payroll.calculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.EmployeeStatutoryProfile;
import com.growdigitalbridge.payroll.domain.PayrollAttendanceInput;
import com.growdigitalbridge.payroll.domain.PayrollException;
import com.growdigitalbridge.payroll.domain.PayrollExceptionReason;
import com.growdigitalbridge.payroll.domain.PayrollLeaveInput;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeStatutoryProfileRepository;
import com.growdigitalbridge.payroll.repository.PayrollAttendanceInputRepository;
import com.growdigitalbridge.payroll.repository.PayrollExceptionRepository;
import com.growdigitalbridge.payroll.repository.PayrollLeaveInputRepository;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.service.PayrollAuditLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Phase 2 calculation pipeline for one {@link PayrollRun}, run as a single database
 * transaction separate from {@code PayrollRunService.process}'s own state-transition writes (see
 * that class for why): Input Resolution -&gt; Compensation Resolution -&gt; Earnings -&gt; Proration -&gt;
 * Deductions -&gt; Employer Contributions -&gt; Gross -&gt; Net -&gt; {@link PayrollRunLine}, per employee in
 * the run's snapshot. An unhandled exception here rolls back every write this method made -
 * partial results are never left behind (item 9); {@code PayrollRunService} is responsible for
 * recording the resulting {@code CALCULATION_FAILED} status afterward, in its own transaction.
 *
 * <p>An employee with no effective compensation is not an error that stops the run (item 10,
 * Section I/X unresolved): it produces a {@link PayrollException} instead of a line, and
 * calculation continues for every other employee. An employee *with* compensation still gets a
 * statutory-profile completeness check (Compensation Management task, item 3/4): a missing
 * profile, or a missing PF/ESI identifier while that scheme is {@code APPLICABLE}/{@code
 * PENDING_VERIFICATION}, or a missing Professional Tax jurisdiction under the same condition,
 * each produces its own {@link PayrollException} *alongside* the normally-calculated line - these
 * never skip or alter the line, they only make the gap visible.
 *
 * <p>Reprocessing is idempotent (item 11): existing lines/exceptions for this run are deleted
 * before recalculating, so a run can be reprocessed any number of times before {@code
 * FINALIZED} without ever accumulating duplicate rows - consistent with decision 8, since nothing
 * before finalization is immutable.
 */
@Service
public class PayrollCalculationEngine {

    private final PayrollRunRepository runRepository;
    private final PayrollPeriodRepository periodRepository;
    private final CompensationResolver compensationResolver;
    private final CompensationComponentRepository componentRepository;
    private final PayrollRunLineRepository lineRepository;
    private final PayrollExceptionRepository exceptionRepository;
    private final PayrollAttendanceInputRepository attendanceInputRepository;
    private final PayrollLeaveInputRepository leaveInputRepository;
    private final EmployeeStatutoryProfileRepository statutoryProfileRepository;
    private final CalculationStrategyRegistry calculationStrategyRegistry;
    private final ProrationPolicyRegistry prorationPolicyRegistry;
    private final PayrollAuditLog auditLog;
    private final ObjectMapper objectMapper;

    public PayrollCalculationEngine(PayrollRunRepository runRepository, PayrollPeriodRepository periodRepository,
                                     CompensationResolver compensationResolver, CompensationComponentRepository componentRepository,
                                     PayrollRunLineRepository lineRepository, PayrollExceptionRepository exceptionRepository,
                                     PayrollAttendanceInputRepository attendanceInputRepository,
                                     PayrollLeaveInputRepository leaveInputRepository,
                                     EmployeeStatutoryProfileRepository statutoryProfileRepository,
                                     CalculationStrategyRegistry calculationStrategyRegistry,
                                     ProrationPolicyRegistry prorationPolicyRegistry, PayrollAuditLog auditLog,
                                     ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.periodRepository = periodRepository;
        this.compensationResolver = compensationResolver;
        this.componentRepository = componentRepository;
        this.lineRepository = lineRepository;
        this.exceptionRepository = exceptionRepository;
        this.attendanceInputRepository = attendanceInputRepository;
        this.leaveInputRepository = leaveInputRepository;
        this.statutoryProfileRepository = statutoryProfileRepository;
        this.calculationStrategyRegistry = calculationStrategyRegistry;
        this.prorationPolicyRegistry = prorationPolicyRegistry;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CalculationResult calculate(UUID runId, String actor, UUID correlationId) {
        PayrollRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Payroll run " + runId + " was not found for calculation."));
        PayrollPeriod period = periodRepository.findById(run.getPeriodId())
                .orElseThrow(() -> new IllegalStateException("Payroll period " + run.getPeriodId() + " was not found for calculation."));

        // Flushed immediately: Hibernate orders all inserts before all deletes within a single
        // flush by default, so without this a reprocess would collide with the (run_id,
        // employee_ref) unique constraint on the very row it just deleted.
        lineRepository.deleteByRunId(runId);
        lineRepository.flush();
        exceptionRepository.deleteByRunId(runId);
        exceptionRepository.flush();

        Instant now = Instant.now();
        int lineCount = 0;
        int exceptionCount = 0;

        for (UUID employeeRef : run.getEmployeeSnapshot()) {
            Optional<EmployeeCompensation> compensation = compensationResolver.resolveEffective(employeeRef, period);
            if (compensation.isEmpty()) {
                saveException(runId, employeeRef, PayrollExceptionReason.NO_EFFECTIVE_COMPENSATION, actor, correlationId, now);
                exceptionCount++;
                continue;
            }

            List<CompensationComponent> components = componentRepository.findByCompensationId(compensation.get().getId());
            List<PayrollAttendanceInput> attendanceInputs =
                    attendanceInputRepository.findByEmployeeRefAndWorkDateBetween(employeeRef, period.getStartDate(), period.getEndDate());
            List<PayrollLeaveInput> leaveInputs = leaveInputRepository.findByEmployeeRef(employeeRef);

            BigDecimal grossPay = BigDecimal.ZERO;
            BigDecimal totalDeductions = BigDecimal.ZERO;
            BigDecimal totalEmployerContributions = BigDecimal.ZERO;
            List<Map<String, Object>> breakdown = new ArrayList<>();

            for (CompensationComponent component : components) {
                ComponentCalculationContext context =
                        new ComponentCalculationContext(period, compensation.get(), component, attendanceInputs, leaveInputs);
                BigDecimal baseAmount = calculationStrategyRegistry.resolveAndCompute(context);
                BigDecimal prorationAdjustment = component.getComponentType() == CompensationComponentType.EARNING
                        ? prorationPolicyRegistry.resolveAndCompute(context)
                        : BigDecimal.ZERO;
                BigDecimal finalAmount = baseAmount.add(prorationAdjustment);

                switch (component.getComponentType()) {
                    case EARNING -> grossPay = grossPay.add(finalAmount);
                    case DEDUCTION -> totalDeductions = totalDeductions.add(finalAmount);
                    case EMPLOYER_CONTRIBUTION -> totalEmployerContributions = totalEmployerContributions.add(finalAmount);
                }

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("componentCode", component.getComponentCode());
                entry.put("componentType", component.getComponentType().name());
                entry.put("baseAmount", baseAmount);
                entry.put("prorationAdjustment", prorationAdjustment);
                entry.put("finalAmount", finalAmount);
                breakdown.add(entry);
            }

            BigDecimal netPay = grossPay.subtract(totalDeductions);
            lineRepository.save(new PayrollRunLine(UUID.randomUUID(), runId, employeeRef, grossPay, totalDeductions,
                    totalEmployerContributions, netPay, writeBreakdown(breakdown), actor, now));
            lineCount++;

            exceptionCount += recordStatutoryProfileGaps(runId, employeeRef, actor, correlationId, now);
        }

        run.markCalculated(actor, now);
        runRepository.save(run);

        return new CalculationResult(lineCount, exceptionCount);
    }

    /**
     * Surfaces statutory-profile data-quality gaps for an employee who otherwise received a
     * normal {@link PayrollRunLine} (item 3/4): a missing profile, or a missing identifier while
     * PF/ESI is {@code APPLICABLE}/{@code PENDING_VERIFICATION} (never inferred as "not
     * applicable" merely because the identifier is absent), or a missing Professional Tax
     * jurisdiction under the same condition. Purely additive visibility - it never skips or alters
     * the line already calculated above, and whether any of these should ever block a run remains
     * PENDING_GDB_APPROVAL (Section X).
     */
    private int recordStatutoryProfileGaps(UUID runId, UUID employeeRef, String actor, UUID correlationId, Instant now) {
        Optional<EmployeeStatutoryProfile> profile = statutoryProfileRepository.findByEmployeeRef(employeeRef);
        if (profile.isEmpty()) {
            saveException(runId, employeeRef, PayrollExceptionReason.MISSING_STATUTORY_PROFILE, actor, correlationId, now);
            return 1;
        }

        int count = 0;
        if (profile.get().isPfIdentifierMissing()) {
            saveException(runId, employeeRef, PayrollExceptionReason.MISSING_PF_IDENTIFIER, actor, correlationId, now);
            count++;
        }
        if (profile.get().isEsiIdentifierMissing()) {
            saveException(runId, employeeRef, PayrollExceptionReason.MISSING_ESI_IDENTIFIER, actor, correlationId, now);
            count++;
        }
        if (profile.get().isPtJurisdictionMissing()) {
            saveException(runId, employeeRef, PayrollExceptionReason.OTHER_CONFIGURATION_ERROR, actor, correlationId, now);
            count++;
        }
        return count;
    }

    private void saveException(UUID runId, UUID employeeRef, PayrollExceptionReason reason, String actor, UUID correlationId, Instant now) {
        exceptionRepository.save(new PayrollException(UUID.randomUUID(), runId, employeeRef, reason, now));
        auditLog.payrollException(runId, employeeRef, reason.name(), actor, correlationId);
    }

    private String writeBreakdown(List<Map<String, Object>> breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payroll run line component breakdown.", e);
        }
    }
}
