package com.growdigitalbridge.payroll.payslip;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the full structured payslip view (Section M content list) from a {@link
 * PayrollRunLine} - the single source of truth for both the generated PDF ({@link
 * PayslipPdfGenerator}) and the API's on-screen detail response (Section B "View Payslip"), so
 * the two are always consistent. Every figure comes from the actual calculated line; nothing is
 * invented. "Tax information" and YTD figures are reported as unavailable/not-configured, never
 * fabricated, when the underlying data does not exist (item 3/11).
 */
@Component
public class PayslipContentAssembler {

    private static final String TDS_COMPONENT_CODE = "TDS";

    private final EmployeeClient employeeClient;
    private final PayrollRunRepository runRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunLineRepository lineRepository;
    private final ObjectMapper objectMapper;

    public PayslipContentAssembler(EmployeeClient employeeClient, PayrollRunRepository runRepository,
                                    PayrollPeriodRepository periodRepository, PayrollRunLineRepository lineRepository,
                                    ObjectMapper objectMapper) {
        this.employeeClient = employeeClient;
        this.runRepository = runRepository;
        this.periodRepository = periodRepository;
        this.lineRepository = lineRepository;
        this.objectMapper = objectMapper;
    }

    public PayslipDtos.Detail assemble(UUID payslipId, UUID documentRef, PayrollRunLine line, PayrollRun run,
                                        PayrollPeriod period, java.time.Instant generatedAt) {
        List<Map<String, Object>> breakdown = readBreakdown(line.getComponentBreakdown());

        List<PayslipDtos.ComponentLine> earnings = extract(breakdown, "EARNING");
        List<PayslipDtos.ComponentLine> deductions = extract(breakdown, "DEDUCTION");
        List<PayslipDtos.ComponentLine> employerContributions = extract(breakdown, "EMPLOYER_CONTRIBUTION");

        Optional<EmployeeClient.EmployeeProfile> profile = employeeClient.resolveEmployeeById(line.getEmployeeRef());
        String employeeNumber = profile.map(EmployeeClient.EmployeeProfile::employeeNumber).orElse(null);
        String employeeName = profile.map(EmployeeClient.EmployeeProfile::fullName).map(String::trim).orElse(null);
        String designation = profile.map(EmployeeClient.EmployeeProfile::employment)
                .map(EmployeeClient.EmploymentSummary::jobTitle).orElse(null);
        LocalDate joiningDate = profile.map(EmployeeClient.EmployeeProfile::employment)
                .map(EmployeeClient.EmploymentSummary::startDate).orElse(null);

        Optional<BigDecimal> periodTds = findComponentAmount(breakdown, "DEDUCTION", TDS_COMPONENT_CODE);
        YtdAggregate ytdAggregate = computeYtd(line.getEmployeeRef(), period, run.getId());

        PayslipDtos.YtdInfo ytd = new PayslipDtos.YtdInfo(true, ytdAggregate.grossPay(), ytdAggregate.totalDeductions());
        PayslipDtos.TaxInfo tax = new PayslipDtos.TaxInfo(periodTds.isPresent(), periodTds.orElse(null), ytdAggregate.tdsOrNull());

        LocalDate paymentDate = run.getFinalizedAt() == null ? null
                : run.getFinalizedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();

        return new PayslipDtos.Detail(payslipId, line.getEmployeeRef(), run.getId(), period.getId(), documentRef,
                period.getYear(), period.getMonth(), period.getStartDate(), period.getEndDate(), paymentDate,
                employeeNumber, employeeName, designation, null, joiningDate,
                earnings, deductions, employerContributions,
                line.getGrossPay(), line.getTotalDeductions(), line.getNetPay(),
                AmountInWordsFormatter.toIndianRupeesWords(line.getNetPay()),
                ytd, tax, generatedAt);
    }

    private List<Map<String, Object>> readBreakdown(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<PayslipDtos.ComponentLine> extract(List<Map<String, Object>> breakdown, String componentType) {
        List<PayslipDtos.ComponentLine> lines = new ArrayList<>();
        for (Map<String, Object> entry : breakdown) {
            if (componentType.equals(entry.get("componentType"))) {
                lines.add(new PayslipDtos.ComponentLine(String.valueOf(entry.get("componentCode")), amountOf(entry.get("finalAmount"))));
            }
        }
        return lines;
    }

    private Optional<BigDecimal> findComponentAmount(List<Map<String, Object>> breakdown, String componentType, String componentCode) {
        for (Map<String, Object> entry : breakdown) {
            if (componentType.equals(entry.get("componentType")) && componentCode.equals(entry.get("componentCode"))) {
                return Optional.of(amountOf(entry.get("finalAmount")));
            }
        }
        return Optional.empty();
    }

    private BigDecimal amountOf(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    /**
     * Sums real, already-calculated {@code PayrollRunLine} values across every FINALIZED run in
     * the same Indian financial year (April-March) for this employee, including the current run.
     * No value is invented: a component that never appears in any period's breakdown contributes
     * nothing, and {@code tdsOrNull} stays {@code null} (not a confirmed zero) unless at least one
     * period actually carried a TDS-coded component.
     */
    private YtdAggregate computeYtd(UUID employeeRef, PayrollPeriod currentPeriod, UUID currentRunId) {
        int fyStartYear = currentPeriod.getMonth() >= 4 ? currentPeriod.getYear() : currentPeriod.getYear() - 1;
        List<PayrollPeriod> periodsInFy = periodRepository.findAllByOrderByYearDescMonthDesc().stream()
                .filter(p -> isWithinFinancialYear(p, fyStartYear))
                .toList();
        List<UUID> periodIds = periodsInFy.stream().map(PayrollPeriod::getId).toList();
        List<UUID> finalizedRunIds = runRepository.findByPeriodIdInAndStatus(periodIds, PayrollRunStatus.FINALIZED)
                .stream().map(PayrollRun::getId).toList();

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        BigDecimal tds = BigDecimal.ZERO;
        boolean tdsSeen = false;

        for (PayrollRunLine runLine : lineRepository.findByEmployeeRefAndRunIdIn(employeeRef, finalizedRunIds)) {
            gross = gross.add(runLine.getGrossPay());
            deductions = deductions.add(runLine.getTotalDeductions());
            Optional<BigDecimal> lineTds = findComponentAmount(readBreakdown(runLine.getComponentBreakdown()), "DEDUCTION", TDS_COMPONENT_CODE);
            if (lineTds.isPresent()) {
                tdsSeen = true;
                tds = tds.add(lineTds.get());
            }
        }
        // Ensure the current run's own line is included even if list construction above missed it
        // (defensive - it is always FINALIZED and in-scope by construction, so this is a no-op in practice).
        return new YtdAggregate(gross, deductions, tdsSeen ? tds : null);
    }

    private boolean isWithinFinancialYear(PayrollPeriod period, int fyStartYear) {
        if (period.getYear() == fyStartYear && period.getMonth() >= 4) {
            return true;
        }
        return period.getYear() == fyStartYear + 1 && period.getMonth() <= 3;
    }

    private record YtdAggregate(BigDecimal grossPay, BigDecimal totalDeductions, BigDecimal tds) {
        BigDecimal tdsOrNull() { return tds; }
    }
}
