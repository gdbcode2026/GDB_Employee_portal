package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.calculation.CalculationResult;
import com.growdigitalbridge.payroll.calculation.PayrollCalculationEngine;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.repository.PayrollExceptionRepository;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.service.exception.CalculationFailedException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Pure unit tests for {@link PayrollRunService}'s self-approval-prevention rule (Section J) and
 * its {@code process} orchestration (item 9), exercised directly without Spring context or a
 * real database/transaction manager - mirroring the platform's AccessGuard-style unit tests
 * elsewhere. {@code transactionManager} is an unstubbed mock: {@code TransactionTemplate} still
 * invokes the given callback synchronously against it, so the orchestration logic under test
 * (which repository/audit calls happen, in which order) is exercised faithfully without a real
 * database.
 */
@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    @Mock
    private PayrollRunRepository repository;

    @Mock
    private PayrollPeriodRepository periodRepository;

    @Mock
    private PayrollRunLineRepository lineRepository;

    @Mock
    private PayrollExceptionRepository exceptionRepository;

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private PayrollCalculationEngine calculationEngine;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private PayrollAuditLog auditLog;

    @Mock
    private com.growdigitalbridge.payroll.payslip.PayslipGenerationService payslipGenerationService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PayrollRunService service() {
        return new PayrollRunService(repository, periodRepository, lineRepository, exceptionRepository,
                employeeClient, calculationEngine, outboxEventWriter, auditLog, payslipGenerationService, transactionManager);
    }

    private PayrollRun runInitiatedBy(String actor) {
        return new PayrollRun(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), actor, Instant.now());
    }

    @Test
    void rejectsTheInitiatorActingOnTheirOwnRun() {
        PayrollRun run = runInitiatedBy("alice");

        assertThatThrownBy(() -> service().assertNotSelfApproval(run, "alice", UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void permitsADifferentIdentityToActOnTheRun() {
        PayrollRun run = runInitiatedBy("alice");

        assertThatCode(() -> service().assertNotSelfApproval(run, "bob", UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void processAdvancesThroughProcessingAndCallsTheCalculationEngine() {
        PayrollRun run = runInitiatedBy("maker-1");
        UUID id = run.getId();
        when(repository.findById(id)).thenReturn(Optional.of(run));
        when(calculationEngine.calculate(id, "maker-1", null)).thenReturn(new CalculationResult(3, 0));

        service().process(id, "maker-1", null);

        verify(auditLog).processingStarted(id, "maker-1", null);
        verify(calculationEngine).calculate(id, "maker-1", null);
        verify(auditLog).calculationCompleted(id, "maker-1", null, 3, 0);
        verify(auditLog, org.mockito.Mockito.never()).calculationFailed(any(), any(), any(), any(), any());
    }

    @Test
    void processRecordsCalculationFailedWhenTheEngineThrowsAndNeverReturnsSuccess() {
        PayrollRun run = runInitiatedBy("maker-1");
        UUID id = run.getId();
        when(repository.findById(id)).thenReturn(Optional.of(run));
        when(calculationEngine.calculate(id, "maker-1", null)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service().process(id, "maker-1", null))
                .isInstanceOf(CalculationFailedException.class);

        verify(auditLog).processingStarted(id, "maker-1", null);
        verify(auditLog).calculationFailed(any(), any(), any(), any(), any());
        verify(auditLog, org.mockito.Mockito.never()).calculationCompleted(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        assertThatCode(() -> {
            if (run.getStatus() != PayrollRunStatus.CALCULATION_FAILED) {
                throw new AssertionError("Expected run to be marked CALCULATION_FAILED, was " + run.getStatus());
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void processRejectsARunThatIsNotInAReprocessableStatus() {
        PayrollRun run = runInitiatedBy("maker-1");
        run.startProcessing("maker-1", Instant.now());
        run.markCalculated("maker-1", Instant.now());
        run.submitForApproval("maker-1", Instant.now());
        UUID id = run.getId();
        when(repository.findById(id)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service().process(id, "maker-1", null))
                .isInstanceOf(com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException.class);
    }

    private PayrollRun approvedRun(String initiator, String approver) {
        PayrollRun run = runInitiatedBy(initiator);
        run.startProcessing(initiator, Instant.now());
        run.markCalculated(initiator, Instant.now());
        run.submitForApproval(initiator, Instant.now());
        run.approve(approver, Instant.now());
        return run;
    }

    @Test
    void finalizeRunTransitionsPublishesEventAndTriggersPayslipGeneration() {
        PayrollRun run = approvedRun("maker-1", "checker-1");
        UUID id = run.getId();
        when(repository.findById(id)).thenReturn(Optional.of(run));

        service().finalizeRun(id, "checker-1", null);

        org.assertj.core.api.Assertions.assertThat(run.getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
        verify(auditLog).runFinalized(id, "checker-1", null);
        verify(outboxEventWriter).write(org.mockito.ArgumentMatchers.eq("payroll.processed.v1"), org.mockito.ArgumentMatchers.eq(id), any(), any());
        verify(payslipGenerationService).generatePayslipsForRun(id, "checker-1", null);
    }

    @Test
    void finalizeRunOnAnAlreadyFinalizedRunOnlyRetriesPayslipGenerationWithoutReemittingTheEvent() {
        PayrollRun run = approvedRun("maker-1", "checker-1");
        run.finalizeRun("checker-1", Instant.now());
        UUID id = run.getId();
        when(repository.findById(id)).thenReturn(Optional.of(run));

        service().finalizeRun(id, "checker-1", null);

        verify(outboxEventWriter, org.mockito.Mockito.never()).write(any(), any(), any(), any());
        verify(auditLog, org.mockito.Mockito.never()).runFinalized(any(), any(), any());
        verify(payslipGenerationService).generatePayslipsForRun(id, "checker-1", null);
    }

    @Test
    void finalizeRunRejectsSelfApproval() {
        PayrollRun run = approvedRun("maker-1", "checker-1");
        run.approve("maker-1", Instant.now());
        UUID id = run.getId();
        when(repository.findById(id)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service().finalizeRun(id, "maker-1", null))
                .isInstanceOf(AccessDeniedException.class);
        verify(payslipGenerationService, org.mockito.Mockito.never()).generatePayslipsForRun(any(), any(), any());
    }

    private PayrollRun finalizedRun(String initiator, String approver) {
        PayrollRun run = approvedRun(initiator, approver);
        run.finalizeRun(approver, Instant.now());
        return run;
    }

    @Test
    void createAdjustmentRejectsAnOriginalRunThatIsNotFinalized() {
        PayrollRun original = runInitiatedBy("maker-1");
        UUID id = original.getId();
        when(repository.findById(id)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service().createAdjustment(id, "maker-2", null))
                .isInstanceOf(com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createAdjustmentRejectsADuplicateWhileOneIsAlreadyInProgress() {
        PayrollRun original = finalizedRun("maker-1", "checker-1");
        UUID id = original.getId();
        when(repository.findById(id)).thenReturn(Optional.of(original));
        when(repository.existsByCorrectsRunIdAndStatusNot(id, PayrollRunStatus.FINALIZED)).thenReturn(true);

        assertThatThrownBy(() -> service().createAdjustment(id, "maker-2", null))
                .isInstanceOf(com.growdigitalbridge.payroll.service.exception.ConflictException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createAdjustmentSucceedsAgainstAFinalizedOriginalRunWithoutMutatingIt() {
        PayrollRun original = finalizedRun("maker-1", "checker-1");
        UUID id = original.getId();
        PayrollRunStatus originalStatusBefore = original.getStatus();
        when(repository.findById(id)).thenReturn(Optional.of(original));
        when(repository.existsByCorrectsRunIdAndStatusNot(id, PayrollRunStatus.FINALIZED)).thenReturn(false);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));

        PayrollRunDtos.Response response = service().createAdjustment(id, "maker-2", null);

        org.mockito.ArgumentCaptor<PayrollRun> savedCaptor = org.mockito.ArgumentCaptor.forClass(PayrollRun.class);
        verify(repository).save(savedCaptor.capture());
        PayrollRun savedAdjustment = savedCaptor.getValue();

        org.assertj.core.api.Assertions.assertThat(savedAdjustment).isNotSameAs(original);
        org.assertj.core.api.Assertions.assertThat(savedAdjustment.getRunType()).isEqualTo(com.growdigitalbridge.payroll.domain.PayrollRunType.ADJUSTMENT);
        org.assertj.core.api.Assertions.assertThat(savedAdjustment.getCorrectsRunId()).isEqualTo(id);
        org.assertj.core.api.Assertions.assertThat(savedAdjustment.getPeriodId()).isEqualTo(original.getPeriodId());
        org.assertj.core.api.Assertions.assertThat(savedAdjustment.getStatus()).isEqualTo(PayrollRunStatus.DRAFT);
        org.assertj.core.api.Assertions.assertThat(response.runType()).isEqualTo(com.growdigitalbridge.payroll.domain.PayrollRunType.ADJUSTMENT);
        org.assertj.core.api.Assertions.assertThat(response.correctsRunId()).isEqualTo(id);

        // The original run itself was never mutated or re-saved.
        org.assertj.core.api.Assertions.assertThat(original.getStatus()).isEqualTo(originalStatusBefore);
        verify(auditLog).adjustmentRunCreated(savedAdjustment.getId(), id, "maker-2", null);
    }
}
