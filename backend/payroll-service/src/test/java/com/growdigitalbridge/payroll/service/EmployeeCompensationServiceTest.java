package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.EmployeeCompensationDtos;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.CompensationStatus;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayComponent;
import com.growdigitalbridge.payroll.domain.PayFrequency;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import com.growdigitalbridge.payroll.repository.PayComponentRepository;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link EmployeeCompensationService}'s validation logic (overlap, effective
 * dates, pay-component reference), exercised directly without a real database - mirroring
 * {@code PayrollRunServiceTest}'s established style for this codebase.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeCompensationServiceTest {

    @Mock private EmployeeCompensationRepository repository;
    @Mock private CompensationComponentRepository componentRepository;
    @Mock private PayComponentRepository payComponentRepository;
    @Mock private PayrollRunLineRepository lineRepository;
    @Mock private PayrollRunRepository runRepository;
    @Mock private PayrollPeriodRepository periodRepository;
    @Mock private PayrollAuditLog auditLog;

    private EmployeeCompensationService service() {
        return new EmployeeCompensationService(repository, componentRepository, payComponentRepository,
                lineRepository, runRepository, periodRepository, auditLog);
    }

    private EmployeeCompensationDtos.ComponentRequest basicSalary(String amount) {
        return new EmployeeCompensationDtos.ComponentRequest("BASIC_SALARY", CompensationComponentType.EARNING,
                new BigDecimal(amount), null, null);
    }

    private PayComponent catalogueEntry(String code, CompensationComponentType type, boolean active) {
        return new PayComponent(UUID.randomUUID(), code, code, type, active, "system", Instant.now());
    }

    @Test
    void createRejectsEffectiveToBeforeEffectiveFrom() {
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(),
                LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 1), List.of(basicSalary("1000.00")));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAnUnknownPayComponentCode() {
        when(payComponentRepository.findByCode("BASIC_SALARY")).thenReturn(Optional.empty());
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(),
                LocalDate.of(2031, 1, 1), null, List.of(basicSalary("1000.00")));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAnInactivePayComponent() {
        when(payComponentRepository.findByCode("BASIC_SALARY"))
                .thenReturn(Optional.of(catalogueEntry("BASIC_SALARY", CompensationComponentType.EARNING, false)));
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(),
                LocalDate.of(2031, 1, 1), null, List.of(basicSalary("1000.00")));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAMismatchedComponentType() {
        when(payComponentRepository.findByCode("BASIC_SALARY"))
                .thenReturn(Optional.of(catalogueEntry("BASIC_SALARY", CompensationComponentType.DEDUCTION, true)));
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(),
                LocalDate.of(2031, 1, 1), null, List.of(basicSalary("1000.00")));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAnOverlappingActiveRecordForTheSameEmployee() {
        UUID employeeRef = UUID.randomUUID();
        when(payComponentRepository.findByCode("BASIC_SALARY"))
                .thenReturn(Optional.of(catalogueEntry("BASIC_SALARY", CompensationComponentType.EARNING, true)));
        EmployeeCompensation existing = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR", PayFrequency.MONTHLY,
                LocalDate.of(2031, 1, 1), null, "hr-1", Instant.now());
        when(repository.findByEmployeeRefOrderByEffectiveFromDesc(employeeRef)).thenReturn(List.of(existing));

        var request = new EmployeeCompensationDtos.CreateRequest(employeeRef,
                LocalDate.of(2031, 6, 1), null, List.of(basicSalary("1000.00")));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void createAllowsARecordThatStartsExactlyAfterAPriorRecordEnds() {
        UUID employeeRef = UUID.randomUUID();
        when(payComponentRepository.findByCode("BASIC_SALARY"))
                .thenReturn(Optional.of(catalogueEntry("BASIC_SALARY", CompensationComponentType.EARNING, true)));
        EmployeeCompensation existing = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR", PayFrequency.MONTHLY,
                LocalDate.of(2031, 1, 1), LocalDate.of(2031, 5, 31), "hr-1", Instant.now());
        when(repository.findByEmployeeRefOrderByEffectiveFromDesc(employeeRef)).thenReturn(List.of(existing));

        var request = new EmployeeCompensationDtos.CreateRequest(employeeRef,
                LocalDate.of(2031, 6, 1), null, List.of(basicSalary("1000.00")));

        assertThatCode(() -> service().create(request, "hr-1", null)).doesNotThrowAnyException();
    }

    @Test
    void createIgnoresAnInactiveExistingRecordWhenCheckingForOverlap() {
        UUID employeeRef = UUID.randomUUID();
        when(payComponentRepository.findByCode("BASIC_SALARY"))
                .thenReturn(Optional.of(catalogueEntry("BASIC_SALARY", CompensationComponentType.EARNING, true)));
        EmployeeCompensation inactive = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR", PayFrequency.MONTHLY,
                LocalDate.of(2031, 1, 1), null, CompensationStatus.INACTIVE, "hr-1", Instant.now());
        when(repository.findByEmployeeRefOrderByEffectiveFromDesc(employeeRef)).thenReturn(List.of(inactive));

        var request = new EmployeeCompensationDtos.CreateRequest(employeeRef,
                LocalDate.of(2031, 2, 1), null, List.of(basicSalary("1000.00")));

        assertThatCode(() -> service().create(request, "hr-1", null)).doesNotThrowAnyException();
    }
}
