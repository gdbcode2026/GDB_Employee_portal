package com.growdigitalbridge.organization.service;

import com.growdigitalbridge.organization.api.dto.ReportingRelationDtos;
import com.growdigitalbridge.organization.domain.ReportingRelation;
import com.growdigitalbridge.organization.domain.ReportingRelationStatus;
import com.growdigitalbridge.organization.repository.ReportingRelationRepository;
import com.growdigitalbridge.organization.service.exception.ConflictException;
import com.growdigitalbridge.organization.service.exception.InvalidReportingRelationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingRelationServiceTest {

    @Mock
    private ReportingRelationRepository repository;

    private ReportingRelationService service() {
        return new ReportingRelationService(repository);
    }

    @Test
    void rejectsSelfManagement() {
        UUID employee = UUID.randomUUID();
        var request = new ReportingRelationDtos.CreateRequest(employee, employee, LocalDate.now());

        assertThatThrownBy(() -> service().create(request, "tester"))
                .isInstanceOf(InvalidReportingRelationException.class);
    }

    @Test
    void rejectsSecondActiveRelationForSameEmployee() {
        UUID employee = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        when(repository.existsByEmployeeRefAndStatus(employee, ReportingRelationStatus.ACTIVE)).thenReturn(true);
        var request = new ReportingRelationDtos.CreateRequest(employee, manager, LocalDate.now());

        assertThatThrownBy(() -> service().create(request, "tester"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAssignmentThatWouldCreateACycle() {
        // Existing chain: b reports to a. Proposed: a reports to b -> cycle.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repository.existsByEmployeeRefAndStatus(a, ReportingRelationStatus.ACTIVE)).thenReturn(false);
        when(repository.findByEmployeeRefAndStatus(b, ReportingRelationStatus.ACTIVE))
                .thenReturn(Optional.of(relation(b, a)));

        var request = new ReportingRelationDtos.CreateRequest(a, b, LocalDate.now());

        assertThatThrownBy(() -> service().create(request, "tester"))
                .isInstanceOf(InvalidReportingRelationException.class);
    }

    @Test
    void allowsAssignmentThatDoesNotCreateACycle() {
        UUID employee = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID grandManager = UUID.randomUUID();
        when(repository.existsByEmployeeRefAndStatus(employee, ReportingRelationStatus.ACTIVE)).thenReturn(false);
        when(repository.findByEmployeeRefAndStatus(manager, ReportingRelationStatus.ACTIVE))
                .thenReturn(Optional.of(relation(manager, grandManager)));
        when(repository.findByEmployeeRefAndStatus(grandManager, ReportingRelationStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new ReportingRelationDtos.CreateRequest(employee, manager, LocalDate.now());

        assertThat(service().create(request, "tester").managerEmployeeRef()).isEqualTo(manager);
    }

    @Test
    void resolvesScopeFromRepositoryWithoutClientSuppliedTeamId() {
        UUID manager = UUID.randomUUID();
        UUID report1 = UUID.randomUUID();
        UUID report2 = UUID.randomUUID();
        when(repository.findReportingScope(manager)).thenReturn(List.of(report1, report2));

        ReportingRelationDtos.ScopeResponse scope = service().resolveScope(manager);

        assertThat(scope.managerEmployeeRef()).isEqualTo(manager);
        assertThat(scope.employeeRefs()).containsExactlyInAnyOrder(report1, report2);
    }

    private ReportingRelation relation(UUID employeeRef, UUID managerEmployeeRef) {
        return new ReportingRelation(UUID.randomUUID(), employeeRef, managerEmployeeRef, LocalDate.now(), "tester", Instant.now());
    }
}
