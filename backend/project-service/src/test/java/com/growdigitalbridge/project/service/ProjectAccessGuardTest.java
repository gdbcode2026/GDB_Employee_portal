package com.growdigitalbridge.project.service;

import com.growdigitalbridge.project.client.EmployeeClient;
import com.growdigitalbridge.project.client.OrganizationClient;
import com.growdigitalbridge.project.domain.MembershipStatus;
import com.growdigitalbridge.project.repository.ProjectMembershipRepository;
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
class ProjectAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrganizationClient organizationClient;

    @Mock
    private ProjectMembershipRepository membershipRepository;

    private ProjectAccessGuard guard() {
        return new ProjectAccessGuard(employeeClient, organizationClient, membershipRepository);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void projectManageCanViewAnyProjectWithoutMembership() {
        var auth = authenticationFor("subject", "project.manage");

        assertThat(guard().canViewProject(auth, UUID.randomUUID())).isTrue();
    }

    @Test
    void projectReadRequiresActiveMembership() {
        UUID self = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        when(membershipRepository.existsByProjectIdAndEmployeeRefAndStatus(projectId, self, MembershipStatus.ACTIVE)).thenReturn(false);
        var auth = authenticationFor("subject", "project.read");

        assertThat(guard().canViewProject(auth, projectId)).isFalse();

        when(membershipRepository.existsByProjectIdAndEmployeeRefAndStatus(projectId, self, MembershipStatus.ACTIVE)).thenReturn(true);
        assertThat(guard().canViewProject(auth, projectId)).isTrue();
    }

    @Test
    void projectListScopeIsUnrestrictedForManageAuthority() {
        var auth = authenticationFor("subject", "project.manage");

        var scope = guard().resolveProjectListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isTrue();
    }

    @Test
    void projectListScopeIsRestrictedToMembershipForReadAuthority() {
        UUID self = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        when(membershipRepository.findProjectIdsByEmployeeRefAndStatus(self, MembershipStatus.ACTIVE)).thenReturn(Set.of(projectId));
        var auth = authenticationFor("subject", "project.read");

        var scope = guard().resolveProjectListScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.allowedProjectIds()).containsExactly(projectId);
    }

    @Test
    void taskScopeUnionsSelfAndTeamWhenBothAuthoritiesHeld() {
        UUID self = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        when(organizationClient.resolveTeamScope(self)).thenReturn(Set.of(teammate));
        var auth = authenticationFor("subject", "task.manage.self", "task.manage.team");

        var scope = guard().resolveTaskScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.assigneeRefs()).containsExactlyInAnyOrder(self, teammate);
    }

    @Test
    void canFullyManageTaskDeniesSelfOnlyAuthority() {
        // task.manage.self alone never qualifies for "full" task management - canFullyManageTask
        // short-circuits on the missing task.manage.team authority without resolving self at all.
        UUID self = UUID.randomUUID();
        var auth = authenticationFor("subject", "task.manage.self");

        assertThat(guard().canFullyManageTask(auth, self)).isFalse();
    }

    @Test
    void canFullyManageTaskGrantsTeamAuthorityWithinScope() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("subject", "task.manage.team");

        assertThat(guard().canFullyManageTask(auth, report)).isTrue();
        assertThat(guard().canFullyManageTask(auth, UUID.randomUUID())).isFalse();
    }
}
