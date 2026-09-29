package com.growdigitalbridge.payroll.payslip;

import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import com.growdigitalbridge.payroll.client.DocumentServiceClient;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.domain.PayslipGenerationFailure;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.repository.PayslipGenerationFailureRepository;
import com.growdigitalbridge.payroll.repository.PayslipRepository;
import com.growdigitalbridge.payroll.service.OutboxEventWriter;
import com.growdigitalbridge.payroll.service.PayrollAuditLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link PayslipGenerationService}'s only-when-FINALIZED guard, per-employee
 * failure isolation, and skip-if-already-generated idempotency (items 2/7/13) - exercised
 * directly, mirroring {@code PayrollRunServiceTest}'s unstubbed-{@code PlatformTransactionManager}
 * convention so {@code TransactionTemplate} still runs each callback synchronously without a real
 * database.
 */
@ExtendWith(MockitoExtension.class)
class PayslipGenerationServiceTest {

    @Mock private PayrollRunLineRepository lineRepository;
    @Mock private PayrollRunRepository runRepository;
    @Mock private PayrollPeriodRepository periodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipGenerationFailureRepository failureRepository;
    @Mock private PayslipContentAssembler contentAssembler;
    @Mock private PayslipPdfGenerator pdfGenerator;
    @Mock private DocumentServiceClient documentServiceClient;
    @Mock private OutboxEventWriter outboxEventWriter;
    @Mock private PayrollAuditLog auditLog;
    @Mock private PlatformTransactionManager transactionManager;

    private PayrollRun run;
    private PayrollPeriod period;

    private PayslipGenerationService service() {
        return new PayslipGenerationService(lineRepository, runRepository, periodRepository, payslipRepository,
                failureRepository, contentAssembler, pdfGenerator, documentServiceClient, outboxEventWriter,
                auditLog, transactionManager);
    }

    @BeforeEach
    void setUp() {
        run = new PayrollRun(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), "maker-1", Instant.now());
        run.startProcessing("maker-1", Instant.now());
        run.markCalculated("maker-1", Instant.now());
        run.submitForApproval("maker-1", Instant.now());
        run.approve("checker-1", Instant.now());
        run.finalizeRun("checker-1", Instant.now());
        period = new PayrollPeriod(run.getPeriodId(), 2026, 3, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                null, "hr-1", Instant.now());
    }

    private void stubFinalizedRunAndPeriod() {
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(periodRepository.findById(run.getPeriodId())).thenReturn(Optional.of(period));
    }

    private PayrollRunLine lineFor(UUID employeeRef) {
        return new PayrollRunLine(UUID.randomUUID(), run.getId(), employeeRef, new BigDecimal("70000.00"),
                new BigDecimal("6000.00"), new BigDecimal("6000.00"), new BigDecimal("64000.00"), "[]", "system", Instant.now());
    }

    private PayslipDtos.Detail detailStub(UUID employeeRef) {
        return new PayslipDtos.Detail(UUID.randomUUID(), employeeRef, run.getId(), period.getId(), null,
                period.getYear(), period.getMonth(), period.getStartDate(), period.getEndDate(), null,
                null, null, null, null, null, List.of(), List.of(), List.of(),
                new BigDecimal("70000.00"), new BigDecimal("6000.00"), new BigDecimal("64000.00"), "Sixty Four Thousand Rupees Only",
                new PayslipDtos.YtdInfo(true, BigDecimal.ZERO, BigDecimal.ZERO), new PayslipDtos.TaxInfo(false, null, null), Instant.now());
    }

    @Test
    void skipsGenerationEntirelyWhenTheRunIsNotFinalized() {
        PayrollRun notFinalized = new PayrollRun(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), "maker-1", Instant.now());
        when(runRepository.findById(notFinalized.getId())).thenReturn(Optional.of(notFinalized));

        PayslipGenerationService.PayslipGenerationSummary summary =
                service().generatePayslipsForRun(notFinalized.getId(), "checker-1", null);

        assertThat(summary.generatedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        verify(lineRepository, never()).findByRunId(any());
        verify(documentServiceClient, never()).createWorkloadUpload(any(), any(), any(), anyLong(), any());
    }

    @Test
    void generatesOnePayslipPerLineUploadsToDocumentServiceAndEmitsExactlyOneEvent() {
        stubFinalizedRunAndPeriod();
        UUID employeeRef = UUID.randomUUID();
        PayrollRunLine line = lineFor(employeeRef);
        when(lineRepository.findByRunId(run.getId())).thenReturn(List.of(line));
        when(payslipRepository.existsByRunIdAndEmployeeRef(run.getId(), employeeRef)).thenReturn(false);
        when(contentAssembler.assemble(any(), any(), eq(line), eq(run), eq(period), any())).thenReturn(detailStub(employeeRef));
        when(pdfGenerator.render(any())).thenReturn(new byte[] {1, 2, 3});
        UUID documentId = UUID.randomUUID();
        when(documentServiceClient.createWorkloadUpload(eq(employeeRef), eq("PAYSLIP"), eq("application/pdf"), eq(3L), anyString()))
                .thenReturn(new DocumentServiceClient.UploadResponse(documentId, employeeRef, "PAYSLIP", "PENDING_SCAN", Instant.now(), Instant.now()));

        PayslipGenerationService.PayslipGenerationSummary summary = service().generatePayslipsForRun(run.getId(), "checker-1", null);

        assertThat(summary.generatedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
        verify(documentServiceClient).completeUpload(eq(documentId), anyString());
        verify(payslipRepository).save(any());
        verify(outboxEventWriter, times(1)).write(eq("payslip.generated.v1"), any(), any(), any());
        verify(auditLog).payslipGenerated(any(), eq(employeeRef), eq(run.getId()), eq("checker-1"), any());
    }

    @Test
    void skipsAnEmployeeThatAlreadyHasAPayslipWithoutRenderingOrCallingDocumentService() {
        stubFinalizedRunAndPeriod();
        UUID employeeRef = UUID.randomUUID();
        when(lineRepository.findByRunId(run.getId())).thenReturn(List.of(lineFor(employeeRef)));
        when(payslipRepository.existsByRunIdAndEmployeeRef(run.getId(), employeeRef)).thenReturn(true);

        PayslipGenerationService.PayslipGenerationSummary summary = service().generatePayslipsForRun(run.getId(), "checker-1", null);

        assertThat(summary.generatedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        verify(pdfGenerator, never()).render(any());
        verify(documentServiceClient, never()).createWorkloadUpload(any(), any(), any(), anyLong(), any());
        verify(payslipRepository, never()).save(any());
    }

    @Test
    void oneEmployeesFailureIsRecordedAndDoesNotBlockAnotherEmployeesSuccess() {
        stubFinalizedRunAndPeriod();
        UUID failingEmployee = UUID.randomUUID();
        UUID succeedingEmployee = UUID.randomUUID();
        PayrollRunLine failingLine = lineFor(failingEmployee);
        PayrollRunLine succeedingLine = lineFor(succeedingEmployee);
        when(lineRepository.findByRunId(run.getId())).thenReturn(List.of(failingLine, succeedingLine));
        when(payslipRepository.existsByRunIdAndEmployeeRef(any(), any())).thenReturn(false);

        when(contentAssembler.assemble(any(), any(), eq(failingLine), eq(run), eq(period), any()))
                .thenThrow(new IllegalStateException("Employee Service unavailable"));
        when(contentAssembler.assemble(any(), any(), eq(succeedingLine), eq(run), eq(period), any()))
                .thenReturn(detailStub(succeedingEmployee));
        when(pdfGenerator.render(any())).thenReturn(new byte[] {1});
        when(documentServiceClient.createWorkloadUpload(eq(succeedingEmployee), any(), any(), anyLong(), anyString()))
                .thenReturn(new DocumentServiceClient.UploadResponse(UUID.randomUUID(), succeedingEmployee, "PAYSLIP", "PENDING_SCAN", Instant.now(), Instant.now()));
        when(failureRepository.findByRunIdAndEmployeeRef(any(), any())).thenReturn(Optional.empty());

        PayslipGenerationService.PayslipGenerationSummary summary = service().generatePayslipsForRun(run.getId(), "checker-1", null);

        assertThat(summary.generatedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isEqualTo(1);
        verify(failureRepository).save(argThatFailureFor(failingEmployee));
        verify(payslipRepository, times(1)).save(any());
        verify(outboxEventWriter, times(1)).write(eq("payslip.generated.v1"), any(), any(), any());
        verify(auditLog).payslipGenerationFailed(eq(run.getId()), eq(failingEmployee), eq("IllegalStateException"), eq("checker-1"), any());
    }

    @Test
    void aSuccessfulRetryDeletesThePreviouslyRecordedFailureForThatEmployee() {
        stubFinalizedRunAndPeriod();
        UUID employeeRef = UUID.randomUUID();
        PayrollRunLine line = lineFor(employeeRef);
        when(lineRepository.findByRunId(run.getId())).thenReturn(List.of(line));
        when(payslipRepository.existsByRunIdAndEmployeeRef(run.getId(), employeeRef)).thenReturn(false);
        when(contentAssembler.assemble(any(), any(), eq(line), eq(run), eq(period), any())).thenReturn(detailStub(employeeRef));
        when(pdfGenerator.render(any())).thenReturn(new byte[] {1});
        when(documentServiceClient.createWorkloadUpload(any(), any(), any(), anyLong(), anyString()))
                .thenReturn(new DocumentServiceClient.UploadResponse(UUID.randomUUID(), employeeRef, "PAYSLIP", "PENDING_SCAN", Instant.now(), Instant.now()));
        PayslipGenerationFailure previousFailure = mock(PayslipGenerationFailure.class);
        when(failureRepository.findByRunIdAndEmployeeRef(run.getId(), employeeRef)).thenReturn(Optional.of(previousFailure));

        service().generatePayslipsForRun(run.getId(), "checker-1", null);

        verify(failureRepository).delete(previousFailure);
    }

    private PayslipGenerationFailure argThatFailureFor(UUID employeeRef) {
        return org.mockito.ArgumentMatchers.argThat(failure -> failure != null && failure.getEmployeeRef().equals(employeeRef));
    }
}
