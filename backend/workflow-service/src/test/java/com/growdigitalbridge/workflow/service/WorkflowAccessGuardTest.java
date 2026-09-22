package com.growdigitalbridge.workflow.service;

import com.growdigitalbridge.workflow.client.EmployeeClient;
import com.growdigitalbridge.workflow.client.OrganizationClient;
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
class WorkflowAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrganizationClient organizationClient;

    private WorkflowAccessGuard guard() {
        return new WorkflowAccessGuard(employeeClient, organizationClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void instanceListScopeIsUnrestrictedForReadAllAuthority() {
        var auth = authenticationFor("subject", "workflow.read.all");

        var scope = guard().resolveInstanceListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isTrue();
    }

    @Test
    void instanceListScopeIsRestrictedToTeamForReadTeamAuthority() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("manager-subject", "workflow.read.team");

        var scope = guard().resolveInstanceListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.requesterRefs()).containsExactly(report);
    }

    @Test
    void instanceListScopeIsDeniedWithoutAnyReadAuthority() {
        var auth = authenticationFor("subject", "workflow.decide.assigned");

        assertThat(guard().resolveInstanceListScope(auth).allowed()).isFalse();
    }

    @Test
    void isWithinCallersTeamScopeDelegatesToOrganizationService() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("manager-subject");

        assertThat(guard().isWithinCallersTeamScope(auth, report)).isTrue();
        assertThat(guard().isWithinCallersTeamScope(auth, UUID.randomUUID())).isFalse();
    }

    @Test
    void hasAuthorityChecksGrantedAuthoritiesOnly() {
        var auth = authenticationFor("subject", "workflow.manage");

        assertThat(guard().hasAuthority(auth, "workflow.manage")).isTrue();
        assertThat(guard().hasAuthority(auth, "workflow.decide.assigned")).isFalse();
    }
}
