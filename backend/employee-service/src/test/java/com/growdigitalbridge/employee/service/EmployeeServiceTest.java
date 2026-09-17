package com.growdigitalbridge.employee.service;

import com.growdigitalbridge.employee.api.dto.EmployeeDtos;
import com.growdigitalbridge.employee.domain.Employee;
import com.growdigitalbridge.employee.domain.EmployeeStatus;
import com.growdigitalbridge.employee.domain.EmergencyContact;
import com.growdigitalbridge.employee.domain.Employment;
import com.growdigitalbridge.employee.domain.EmploymentStatus;
import com.growdigitalbridge.employee.domain.EmploymentType;
import com.growdigitalbridge.employee.repository.EmergencyContactRepository;
import com.growdigitalbridge.employee.repository.EmployeeRepository;
import com.growdigitalbridge.employee.repository.EmploymentRepository;
import com.growdigitalbridge.employee.service.exception.ConflictException;
import com.growdigitalbridge.employee.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.employee.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmploymentRepository employmentRepository;
    @Mock private EmergencyContactRepository emergencyContactRepository;
    @Mock private EmployeeAccessGuard accessGuard;
    @Mock private OutboxEventWriter outboxEventWriter;
    @Mock private Authentication authentication;

    private EmployeeService service() {
        return new EmployeeService(employeeRepository, employmentRepository, emergencyContactRepository, accessGuard, outboxEventWriter);
    }

    private EmployeeDtos.CreateRequest createRequest() {
        return new EmployeeDtos.CreateRequest("EMP-1", "Ada", "Lovelace", "ada@example.com", null, null,
                new EmployeeDtos.EmploymentDetails("Engineer", EmploymentType.FULL_TIME, LocalDate.now()), null);
    }

    @Test
    void createPersistsEmployeeAndEmploymentAndWritesOutboxEvent() {
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(employmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emergencyContactRepository.findByEmployeeId(any())).thenReturn(List.of());

        EmployeeDtos.Response response = service().create(createRequest(), "hr-user", UUID.randomUUID());

        assertThat(response.employeeNumber()).isEqualTo("EMP-1");
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
        verify(outboxEventWriter).write(eq("employee.created.v1"), any(), any(), any());
    }

    @Test
    void createRejectsDuplicateEmployeeNumber() {
        when(employeeRepository.existsByEmployeeNumber("EMP-1")).thenReturn(true);

        assertThatThrownBy(() -> service().create(createRequest(), "hr-user", UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
        verify(outboxEventWriter, never()).write(anyString(), any(), any(), any());
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service().create(createRequest(), "hr-user", UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsDuplicateIdentitySubject() {
        var request = new EmployeeDtos.CreateRequest("EMP-2", "Grace", "Hopper", "grace@example.com", null, "idp-subject-1",
                new EmployeeDtos.EmploymentDetails("Engineer", EmploymentType.FULL_TIME, LocalDate.now()), null);
        when(employeeRepository.existsByIdentitySubject("idp-subject-1")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request, "hr-user", UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateByAdminDeactivatesActiveEmployeeAndEndsEmployment() {
        UUID id = UUID.randomUUID();
        Employee employee = new Employee(id, "EMP-1", "Ada", "Lovelace", "ada@example.com", null, null, "hr", Instant.now());
        Employment employment = new Employment(UUID.randomUUID(), id, "Engineer", EmploymentType.FULL_TIME, LocalDate.now(), "hr", Instant.now());
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));
        when(employmentRepository.findByEmployeeIdAndStatus(id, EmploymentStatus.ACTIVE)).thenReturn(Optional.of(employment));
        when(emergencyContactRepository.findByEmployeeId(id)).thenReturn(List.of());

        var request = new EmployeeDtos.AdminUpdateRequest(null, null, null, null, EmployeeStatus.INACTIVE, null, null);
        EmployeeDtos.Response response = service().updateByAdmin(id, request, "hr-user", UUID.randomUUID());

        assertThat(response.status()).isEqualTo(EmployeeStatus.INACTIVE);
        assertThat(employment.getStatus()).isEqualTo(EmploymentStatus.ENDED);
        verify(outboxEventWriter).write(eq("employee.deactivated.v1"), any(), any(), any());
        verify(outboxEventWriter, never()).write(eq("employee.updated.v1"), any(), any(), any());
    }

    @Test
    void updateByAdminRejectsReactivation() {
        UUID id = UUID.randomUUID();
        Employee employee = new Employee(id, "EMP-1", "Ada", "Lovelace", "ada@example.com", null, null, "hr", Instant.now());
        employee.deactivate("hr", Instant.now());
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));

        var request = new EmployeeDtos.AdminUpdateRequest(null, null, null, null, EmployeeStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> service().updateByAdmin(id, request, "hr-user", UUID.randomUUID()))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }

    @Test
    void updateByAdminThrowsNotFoundForUnknownEmployee() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        var request = new EmployeeDtos.AdminUpdateRequest("New name", null, null, null, null, null, null);

        assertThatThrownBy(() -> service().updateByAdmin(id, request, "hr-user", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSelfUpdatesPhoneAndFiresUpdatedEvent() {
        UUID id = UUID.randomUUID();
        Employee employee = new Employee(id, "EMP-1", "Ada", "Lovelace", "ada@example.com", "000", "subject-1", "self", Instant.now());
        when(accessGuard.resolveSelf(authentication)).thenReturn(Optional.of(employee));
        when(emergencyContactRepository.findByEmployeeId(id)).thenReturn(List.of());

        var request = new EmployeeDtos.SelfUpdateRequest("999", null);
        EmployeeDtos.Response response = service().updateSelf(authentication, request, "subject-1", UUID.randomUUID());

        assertThat(response.phone()).isEqualTo("999");
        verify(outboxEventWriter).write(eq("employee.updated.v1"), any(), any(), any());
    }

    @Test
    void updateSelfThrowsNotFoundWhenNoLinkedProfile() {
        when(accessGuard.resolveSelf(authentication)).thenReturn(Optional.empty());
        var request = new EmployeeDtos.SelfUpdateRequest("999", null);

        assertThatThrownBy(() -> service().updateSelf(authentication, request, "subject-1", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listThrowsAccessDeniedWhenScopeIsDenied() {
        when(accessGuard.resolveListScope(authentication)).thenReturn(EmployeeAccessGuard.ListScope.denied());

        assertThatThrownBy(() -> service().list(authentication, null, null, org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listReturnsEmptyPageWithoutQueryingRepositoryWhenTeamScopeIsEmpty() {
        when(accessGuard.resolveListScope(authentication)).thenReturn(EmployeeAccessGuard.ListScope.restrictedTo(java.util.Set.of()));

        var page = service().list(authentication, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.items()).isEmpty();
        verify(employeeRepository, never()).searchWithinScope(any(), any(), any(), any());
        verify(employeeRepository, never()).searchAll(any(), any(), any());
    }

    @Test
    void getByIdMasksExistenceAsNotFoundWhenNotVisible() {
        UUID id = UUID.randomUUID();
        Employee employee = new Employee(id, "EMP-1", "Ada", "Lovelace", "ada@example.com", null, null, "hr", Instant.now());
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));
        when(accessGuard.canRead(authentication, employee)).thenReturn(false);

        assertThatThrownBy(() -> service().getById(id, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
