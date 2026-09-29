package com.growdigitalbridge.payroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayslipDtos {

    private PayslipDtos() { }

    /** Lightweight list-view row for {@code GET /payroll/payslips/me}. */
    public record Summary(UUID id, UUID runId, UUID periodId, int periodYear, int periodMonth, Instant generatedAt) { }

    public record ComponentLine(String code, BigDecimal amount) { }

    /** Year-to-date totals (Section Q). Only ever populated from real finalized data - see {@code available}. */
    public record YtdInfo(boolean available, BigDecimal grossPay, BigDecimal totalDeductions) { }

    /**
     * Applicable tax information (Section M/decision 12 locks the *field*; the *value* depends on
     * pending statutory rules - Section X). {@code configured=false} means no TDS-coded component
     * exists for this employee/run - the amount is safely reported as not configured, never zero
     * asserted as a confirmed tax outcome.
     */
    public record TaxInfo(boolean configured, BigDecimal periodAmount, BigDecimal ytdAmount) { }

    /** Full structured on-screen view for {@code GET /payroll/payslips/{id}} (Section B: "View Payslip"). */
    public record Detail(
            UUID id, UUID employeeRef, UUID runId, UUID periodId, UUID documentRef,
            int periodYear, int periodMonth, LocalDate periodStart, LocalDate periodEnd, LocalDate paymentDate,
            String employeeNumber, String employeeName, String designation, String department, LocalDate joiningDate,
            List<ComponentLine> earnings, List<ComponentLine> deductions, List<ComponentLine> employerContributions,
            BigDecimal grossPay, BigDecimal totalDeductions, BigDecimal netPay, String amountInWords,
            YtdInfo ytd, TaxInfo tax, Instant generatedAt) { }

    public record DownloadResponse(UUID payslipId, UUID documentId, String objectKey, String checksum, String mimeType, long sizeBytes) { }
}
