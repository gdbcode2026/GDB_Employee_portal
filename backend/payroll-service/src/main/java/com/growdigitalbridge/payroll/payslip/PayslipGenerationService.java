package com.growdigitalbridge.payroll.payslip;

import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import com.growdigitalbridge.payroll.client.DocumentServiceClient;
import com.growdigitalbridge.payroll.domain.Payslip;
import com.growdigitalbridge.payroll.domain.PayslipGenerationFailure;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.repository.PayslipGenerationFailureRepository;
import com.growdigitalbridge.payroll.repository.PayslipRepository;
import com.growdigitalbridge.payroll.service.OutboxEventWriter;
import com.growdigitalbridge.payroll.service.PayrollAuditLog;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates a {@link Payslip} for every {@link PayrollRunLine} of a FINALIZED run that does not
 * already have one (item 2/13: only-when-FINALIZED, idempotent). Each employee is processed in
 * its own transaction, exactly like {@code PayrollRunService}'s own orchestration pattern - one
 * employee's failure (a PDF/Document-Service/Employee-Service problem) never rolls back another
 * employee's already-successful payslip, and never touches the already-FINALIZED run itself
 * (item 7: "do not recalculate payroll", "do not make FINALIZED payroll mutable"). Calling this
 * again (e.g. via retrying {@code POST /payroll/runs/{id}/finalize}) is the retry mechanism: it
 * simply re-attempts every line that still has no payslip.
 */
@Service
public class PayslipGenerationService {

    private static final String CLASSIFICATION = "PAYSLIP";
    private static final String MIME_TYPE = "application/pdf";

    private final PayrollRunLineRepository lineRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipGenerationFailureRepository failureRepository;
    private final PayslipContentAssembler contentAssembler;
    private final PayslipPdfGenerator pdfGenerator;
    private final DocumentServiceClient documentServiceClient;
    private final OutboxEventWriter outboxEventWriter;
    private final PayrollAuditLog auditLog;
    private final TransactionTemplate transactionTemplate;

    public PayslipGenerationService(PayrollRunLineRepository lineRepository, PayrollRunRepository runRepository,
                                     PayrollPeriodRepository periodRepository, PayslipRepository payslipRepository,
                                     PayslipGenerationFailureRepository failureRepository,
                                     PayslipContentAssembler contentAssembler, PayslipPdfGenerator pdfGenerator,
                                     DocumentServiceClient documentServiceClient, OutboxEventWriter outboxEventWriter,
                                     PayrollAuditLog auditLog, PlatformTransactionManager transactionManager) {
        this.lineRepository = lineRepository;
        this.runRepository = runRepository;
        this.periodRepository = periodRepository;
        this.payslipRepository = payslipRepository;
        this.failureRepository = failureRepository;
        this.contentAssembler = contentAssembler;
        this.pdfGenerator = pdfGenerator;
        this.documentServiceClient = documentServiceClient;
        this.outboxEventWriter = outboxEventWriter;
        this.auditLog = auditLog;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PayslipGenerationSummary generatePayslipsForRun(UUID runId, String actor, UUID correlationId) {
        PayrollRun run = runRepository.findById(runId).orElseThrow();
        if (run.getStatus() != PayrollRunStatus.FINALIZED) {
            // Defensive only: the only caller (PayrollRunService.finalizeRun) never reaches this
            // otherwise - never generate an employee-visible payslip for a non-FINALIZED run.
            return new PayslipGenerationSummary(0, 0);
        }
        PayrollPeriod period = periodRepository.findById(run.getPeriodId()).orElseThrow();

        int generated = 0;
        int failed = 0;
        for (PayrollRunLine line : lineRepository.findByRunId(runId)) {
            if (payslipRepository.existsByRunIdAndEmployeeRef(runId, line.getEmployeeRef())) {
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> generateOne(run, period, line, actor, correlationId));
                transactionTemplate.executeWithoutResult(status ->
                        failureRepository.findByRunIdAndEmployeeRef(runId, line.getEmployeeRef()).ifPresent(failureRepository::delete));
                generated++;
            } catch (RuntimeException e) {
                recordFailure(runId, line.getEmployeeRef(), e, actor, correlationId);
                failed++;
            }
        }
        return new PayslipGenerationSummary(generated, failed);
    }

    private void generateOne(PayrollRun run, PayrollPeriod period, PayrollRunLine line, String actor, UUID correlationId) {
        Instant now = Instant.now();
        PayslipDtos.Detail content = contentAssembler.assemble(UUID.randomUUID(), null, line, run, period, now);
        byte[] pdfBytes = pdfGenerator.render(content);
        String checksum = sha256Hex(pdfBytes);

        DocumentServiceClient.UploadResponse upload = documentServiceClient.createWorkloadUpload(
                line.getEmployeeRef(), CLASSIFICATION, MIME_TYPE, pdfBytes.length, checksum);
        documentServiceClient.uploadContent(upload.id(), pdfBytes, MIME_TYPE);
        documentServiceClient.completeUpload(upload.id(), checksum);

        UUID payslipId = UUID.randomUUID();
        payslipRepository.save(new Payslip(payslipId, line.getEmployeeRef(), run.getId(), period.getId(), upload.id(), now));
        auditLog.payslipGenerated(payslipId, line.getEmployeeRef(), run.getId(), actor, correlationId);

        outboxEventWriter.write("payslip.generated.v1", payslipId, Map.of(
                "payslipId", payslipId.toString(),
                "employeeId", line.getEmployeeRef().toString(),
                "runId", run.getId().toString(),
                "periodId", period.getId().toString(),
                "generatedAt", now.toString()), correlationId);
    }

    private void recordFailure(UUID runId, UUID employeeRef, RuntimeException e, String actor, UUID correlationId) {
        String type = e.getClass().getSimpleName();
        String message = e.getMessage();
        auditLog.payslipGenerationFailed(runId, employeeRef, type, actor, correlationId);
        transactionTemplate.executeWithoutResult(status -> {
            failureRepository.findByRunIdAndEmployeeRef(runId, employeeRef).ifPresent(existing -> {
                failureRepository.delete(existing);
                failureRepository.flush();
            });
            failureRepository.save(new PayslipGenerationFailure(UUID.randomUUID(), runId, employeeRef, type, message, Instant.now()));
        });
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    public record PayslipGenerationSummary(int generatedCount, int failedCount) { }
}
