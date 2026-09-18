package com.growdigitalbridge.leave.service;

import com.growdigitalbridge.leave.client.EmployeeClient;
import com.growdigitalbridge.leave.client.OrganizationClient;
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
class LeaveAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrganizationClient organizationClient;

    private LeaveAccessGuard guard() {
        return new LeaveAccessGuard(employeeClient, organizationClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void canActOnBehalfOfAlwaysQualifiesForTheAllAuthority() {
        var auth = authenticationFor("subject", "leave.approve.all");

        assertThat(guard().canActOnBehalfOf(auth, UUID.randomUUID(), "leave.approve.team", "leave.approve.all")).isTrue();
    }

    @Test
    void canActOnBehalfOfRequiresOrganizationScopeForTheTeamAuthority() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());
        var auth = authenticationFor("manager-subject", "leave.approve.team");

        assertThat(guard().canActOnBehalfOf(auth, report, "leave.approve.team", "leave.approve.all")).isFalse();

        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        assertThat(guard().canActOnBehalfOf(auth, report, "leave.approve.team", "leave.approve.all")).isTrue();
    }

    @Test
    void listScopeIsUnrestrictedForReadAllAuthority() {
        var auth = authenticationFor("subject", "leave.read.all");

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
        var auth = authenticationFor("manager-subject", "leave.read.team");

        var scope = guard().resolveListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.allowedIds()).containsExactly(reportId);
    }

    @Test
    void listScopeIsDeniedWithoutAnyReadAuthority() {
        var auth = authenticationFor("subject", "leave.create.self");

        assertThat(guard().resolveListScope(auth).allowed()).isFalse();
    }
}
