package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import com.growdigitalbridge.payroll.client.DocumentServiceClient;
import com.growdigitalbridge.payroll.domain.Payslip;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.payslip.PayslipContentAssembler;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.repository.PayslipRepository;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee-facing payslip read access (item 8): {@code GET /payroll/payslips/me},
 * {@code GET /payroll/payslips/{id}}, {@code GET /payroll/payslips/{id}/download}. Every method
 * resolves "self" server-side via Employee Service (never a client-supplied identity, item 10)
 * and defers the view/download decision entirely to {@link PayslipAccessGuard}. There is no
 * public/unauthenticated URL anywhere in this flow - download re-uses Document Service's own
 * short-lived, already-secured {@code GET /documents/{id}/download} contract via {@link
 * DocumentServiceClient#downloadOnBehalfOfCaller}.
 */
@Service
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunLineRepository lineRepository;
    private final PayslipContentAssembler contentAssembler;
    private final DocumentServiceClient documentServiceClient;
    private final PayslipAccessGuard accessGuard;
    private final PayrollAuditLog auditLog;

    public PayslipService(PayslipRepository payslipRepository, PayrollRunRepository runRepository,
                           PayrollPeriodRepository periodRepository, PayrollRunLineRepository lineRepository,
                           PayslipContentAssembler contentAssembler, DocumentServiceClient documentServiceClient,
                           PayslipAccessGuard accessGuard, PayrollAuditLog auditLog) {
        this.payslipRepository = payslipRepository;
        this.runRepository = runRepository;
        this.periodRepository = periodRepository;
        this.lineRepository = lineRepository;
        this.contentAssembler = contentAssembler;
        this.documentServiceClient = documentServiceClient;
        this.accessGuard = accessGuard;
        this.auditLog = auditLog;
    }

    /** Item 9: optional {@code periodId} or {@code financialYearStart} filter, standard pagination. */
    @Transactional(readOnly = true)
    public PageResponse<PayslipDtos.Summary> listMine(UUID periodId, Integer financialYearStart, Pageable pageable) {
        UUID self = accessGuard.resolveSelf()
                .orElseThrow(() -> new AccessDeniedException("Unable to resolve the caller's own employee reference."));

        Page<Payslip> page;
        if (periodId != null) {
            page = payslipRepository.findByEmployeeRefAndPeriodId(self, periodId, pageable);
        } else if (financialYearStart != null) {
            List<UUID> periodIds = periodRepository.findAllByOrderByYearDescMonthDesc().stream()
                    .filter(p -> isWithinFinancialYear(p, financialYearStart))
                    .map(PayrollPeriod::getId)
                    .toList();
            page = payslipRepository.findByEmployeeRefAndPeriodIdIn(self, periodIds, pageable);
        } else {
            page = payslipRepository.findByEmployeeRef(self, pageable);
        }
        return PageResponse.of(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public PayslipDtos.Detail getById(UUID id, Authentication authentication, String actor, UUID correlationId) {
        Payslip payslip = find(id);
        authorizeOrThrow(authentication, payslip.getEmployeeRef());
        auditLog.payslipViewed(payslip.getId(), payslip.getEmployeeRef(), viewerRole(payslip.getEmployeeRef()), actor, correlationId);
        return assemble(payslip);
    }

    @Transactional(readOnly = true)
    public PayslipDtos.DownloadResponse download(UUID id, Authentication authentication, String actor, UUID correlationId) {
        Payslip payslip = find(id);
        authorizeOrThrow(authentication, payslip.getEmployeeRef());
        DocumentServiceClient.DownloadResponse response = documentServiceClient.downloadOnBehalfOfCaller(payslip.getDocumentRef());
        auditLog.payslipDownloaded(payslip.getId(), payslip.getEmployeeRef(), viewerRole(payslip.getEmployeeRef()), actor, correlationId);
        return new PayslipDtos.DownloadResponse(payslip.getId(), response.documentId(), response.objectKey(),
                response.checksum(), response.mimeType(), response.sizeBytes());
    }

    private void authorizeOrThrow(Authentication authentication, UUID employeeRef) {
        if (!accessGuard.canView(authentication, employeeRef)) {
            throw new AccessDeniedException("Not authorized to view this payslip.");
        }
    }

    private String viewerRole(UUID employeeRef) {
        boolean isSelf = accessGuard.resolveSelf().map(self -> self.equals(employeeRef)).orElse(false);
        return isSelf ? "SELF" : "HR_FINANCE";
    }

    private PayslipDtos.Detail assemble(Payslip payslip) {
        PayrollRun run = runRepository.findById(payslip.getRunId())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run " + payslip.getRunId() + " was not found."));
        PayrollPeriod period = periodRepository.findById(payslip.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period " + payslip.getPeriodId() + " was not found."));
        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(payslip.getRunId(), payslip.getEmployeeRef())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run line for run " + payslip.getRunId() + " was not found."));
        return contentAssembler.assemble(payslip.getId(), payslip.getDocumentRef(), line, run, period, payslip.getGeneratedAt());
    }

    private PayslipDtos.Summary toSummary(Payslip payslip) {
        PayrollPeriod period = periodRepository.findById(payslip.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period " + payslip.getPeriodId() + " was not found."));
        return new PayslipDtos.Summary(payslip.getId(), payslip.getRunId(), payslip.getPeriodId(),
                period.getYear(), period.getMonth(), payslip.getGeneratedAt());
    }

    private Payslip find(UUID id) {
        return payslipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip " + id + " was not found."));
    }

    private boolean isWithinFinancialYear(PayrollPeriod period, int fyStartYear) {
        if (period.getYear() == fyStartYear && period.getMonth() >= 4) {
            return true;
        }
        return period.getYear() == fyStartYear + 1 && period.getMonth() <= 3;
    }
}
