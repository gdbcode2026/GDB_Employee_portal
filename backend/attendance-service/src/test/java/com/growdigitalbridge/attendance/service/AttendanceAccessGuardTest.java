package com.growdigitalbridge.attendance.service;

import com.growdigitalbridge.attendance.client.EmployeeClient;
import com.growdigitalbridge.attendance.client.OrganizationClient;
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
class AttendanceAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrganizationClient organizationClient;

    private AttendanceAccessGuard guard() {
        return new AttendanceAccessGuard(employeeClient, organizationClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void allAuthorityGrantsReadRegardlessOfSelfOrTeam() {
        var auth = authenticationFor("some-subject", "attendance.read.all");

        assertThat(guard().canRead(auth, UUID.randomUUID())).isTrue();
    }

    @Test
    void selfAuthorityGrantsReadOnlyForOwnRecord() {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject-1", "attendance.read.self");

        assertThat(guard().canRead(auth, self)).isTrue();
    }

    @Test
    void selfAuthorityDeniesReadForSomeoneElsesRecord() {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        var auth = authenticationFor("subject-1", "attendance.read.self");

        assertThat(guard().canRead(auth, UUID.randomUUID())).isFalse();
    }

    @Test
    void teamAuthorityGrantsReadOnlyWhenOrganizationServiceIncludesTargetInScope() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("manager-subject", "attendance.read.team");

        assertThat(guard().canRead(auth, report)).isTrue();
    }

    @Test
    void teamAuthorityDeniesReadWhenOrganizationServiceExcludesTarget() {
        UUID manager = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());
        var auth = authenticationFor("manager-subject", "attendance.read.team");

        assertThat(guard().canRead(auth, UUID.randomUUID())).isFalse();
    }

    @Test
    void canFinalizeRequiresBothTeamAuthorityAndOrganizationScope() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var withFinalize = authenticationFor("manager-subject", "attendance.finalize.team");
        var withoutFinalize = authenticationFor("manager-subject", "attendance.read.team");

        assertThat(guard().canFinalize(withFinalize, report)).isTrue();
        assertThat(guard().canFinalize(withoutFinalize, report)).isFalse();
    }

    @Test
    void listScopeIsUnrestrictedForReadAllAuthority() {
        var auth = authenticationFor("subject", "attendance.read.all");

        var scope = guard().resolveListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isTrue();
    }

    @Test
    void listScopeIsRestrictedToOrganizationResolvedIdsForTeamAuthority() {
        UUID manager = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(reportId));
        var auth = authenticationFor("manager-subject", "attendance.read.team");

        var scope = guard().resolveListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.allowedIds()).containsExactly(reportId);
    }

    @Test
    void listScopeIsRestrictedToSelfForSelfOnlyAuthority() {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject", "attendance.read.self");

        var scope = guard().resolveListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.allowedIds()).containsExactly(self);
    }

    @Test
    void listScopeIsDeniedWithoutAnyReadAuthority() {
        var auth = authenticationFor("subject", "attendance.create.self");

        assertThat(guard().resolveListScope(auth).allowed()).isFalse();
    }
}
