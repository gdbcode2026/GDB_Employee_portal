package com.growdigitalbridge.employee.service;

import com.growdigitalbridge.employee.client.OrganizationClient;
import com.growdigitalbridge.employee.domain.Employee;
import com.growdigitalbridge.employee.repository.EmployeeRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAccessGuardTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private OrganizationClient organizationClient;

    private EmployeeAccessGuard guard() {
        return new EmployeeAccessGuard(employeeRepository, organizationClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    private Employee employee(UUID id) {
        return new Employee(id, "E1", "First", "Last", "first@example.com", null, null, "tester", Instant.now());
    }

    @Test
    void allAuthorityGrantsReadRegardlessOfSelfOrTeam() {
        Employee target = employee(UUID.randomUUID());
        var auth = authenticationFor("some-subject", "employee.read.all");

        assertThat(guard().canRead(auth, target)).isTrue();
    }

    @Test
    void selfAuthorityGrantsReadOnlyForOwnRecord() {
        UUID id = UUID.randomUUID();
        Employee self = employee(id);
        when(employeeRepository.findByIdentitySubject("subject-1")).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject-1", "employee.read.self");

        assertThat(guard().canRead(auth, self)).isTrue();
    }

    @Test
    void selfAuthorityDeniesReadForSomeoneElsesRecord() {
        Employee self = employee(UUID.randomUUID());
        Employee other = employee(UUID.randomUUID());
        when(employeeRepository.findByIdentitySubject("subject-1")).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject-1", "employee.read.self");

        assertThat(guard().canRead(auth, other)).isFalse();
    }

    @Test
    void teamAuthorityGrantsReadOnlyWhenOrganizationServiceIncludesTargetInScope() {
        Employee manager = employee(UUID.randomUUID());
        Employee report = employee(UUID.randomUUID());
        when(employeeRepository.findByIdentitySubject("manager-subject")).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager.getId())).thenReturn(Set.of(report.getId()));
        var auth = authenticationFor("manager-subject", "employee.read.team");

        assertThat(guard().canRead(auth, report)).isTrue();
    }

    @Test
    void teamAuthorityDeniesReadWhenOrganizationServiceExcludesTarget() {
        Employee manager = employee(UUID.randomUUID());
        Employee stranger = employee(UUID.randomUUID());
        when(employeeRepository.findByIdentitySubject("manager-subject")).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager.getId())).thenReturn(Set.of());
        var auth = authenticationFor("manager-subject", "employee.read.team");

        assertThat(guard().canRead(auth, stranger)).isFalse();
    }

    @Test
    void listScopeIsUnrestrictedForReadAllAuthority() {
        var auth = authenticationFor("subject", "employee.read.all");

        var scope = guard().resolveListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isTrue();
    }

    @Test
    void listScopeIsRestrictedToOrganizationResolvedIdsForTeamAuthority() {
        Employee manager = employee(UUID.randomUUID());
        UUID reportId = UUID.randomUUID();
        when(employeeRepository.findByIdentitySubject("manager-subject")).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager.getId())).thenReturn(Set.of(reportId));
        var auth = authenticationFor("manager-subject", "employee.read.team");

        var scope = guard().resolveListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.allowedIds()).containsExactly(reportId);
    }

    @Test
    void listScopeIsDeniedForSelfOnlyAuthority() {
        var auth = authenticationFor("subject", "employee.read.self");

        assertThat(guard().resolveListScope(auth).allowed()).isFalse();
    }
}
