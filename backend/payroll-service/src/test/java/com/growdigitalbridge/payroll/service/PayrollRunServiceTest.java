package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test for the self-approval-prevention rule (PAYROLL_REQUIREMENTS.md Section J) -
 * exercised directly without Spring context, mirroring the platform's AccessGuard-style unit
 * tests elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    @Mock
    private PayrollRunRepository repository;

    @Mock
    private PayrollPeriodRepository periodRepository;

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private PayrollAuditLog auditLog;

    private PayrollRunService service() {
        return new PayrollRunService(repository, periodRepository, employeeClient, outboxEventWriter, auditLog);
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
}
