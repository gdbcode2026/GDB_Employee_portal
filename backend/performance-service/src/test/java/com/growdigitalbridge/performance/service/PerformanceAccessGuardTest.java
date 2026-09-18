package com.growdigitalbridge.performance.service;

import com.growdigitalbridge.performance.client.EmployeeClient;
import com.growdigitalbridge.performance.client.OrganizationClient;
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
class PerformanceAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrganizationClient organizationClient;

    private PerformanceAccessGuard guard() {
        return new PerformanceAccessGuard(employeeClient, organizationClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void goalReadScopeIsUnrestrictedForReadAllAuthority() {
        var auth = authenticationFor("subject", "performance.read.all");

        var scope = guard().resolveGoalReadScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isTrue();
    }

    @Test
    void goalReadScopeIsRestrictedToTeamForReadTeamAuthority() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("manager-subject", "performance.read.team");

        var scope = guard().resolveGoalReadScope(auth);

        assertThat(scope.allowed()).isTrue();
        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.employeeRefs()).containsExactly(report);
    }

    @Test
    void goalReadScopeIsDeniedWithoutAnyReadAuthority() {
        var auth = authenticationFor("subject", "performance.goal.manage.self");

        assertThat(guard().resolveGoalReadScope(auth).allowed()).isFalse();
    }

    @Test
    void reviewReadScopeSelfLevelResolvesCallersOwnId() {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject", "performance.read.self");

        var scope = guard().resolveReviewReadScope(auth);

        assertThat(scope.level()).isEqualTo(PerformanceAccessGuard.ReviewReadLevel.SELF);
        assertThat(scope.self()).isEqualTo(self);
    }

    @Test
    void canReviewAllowsSelfReview() {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject", "performance.review.submit.team");

        assertThat(guard().canReview(auth, self)).isTrue();
    }

    @Test
    void canReviewRequiresOrganizationTeamScopeForOthers() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("manager-subject", "performance.review.submit.team");

        assertThat(guard().canReview(auth, report)).isTrue();
        assertThat(guard().canReview(auth, stranger)).isFalse();
    }

    @Test
    void canManageAllBypassesEverything() {
        var auth = authenticationFor("subject", "performance.manage");

        assertThat(guard().canReview(auth, UUID.randomUUID())).isTrue();
    }
}
