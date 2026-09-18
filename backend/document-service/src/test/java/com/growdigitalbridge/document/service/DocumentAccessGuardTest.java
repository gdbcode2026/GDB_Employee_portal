package com.growdigitalbridge.document.service;

import com.growdigitalbridge.document.client.EmployeeClient;
import com.growdigitalbridge.document.client.OrganizationClient;
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
class DocumentAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrganizationClient organizationClient;

    private DocumentAccessGuard guard() {
        return new DocumentAccessGuard(employeeClient, organizationClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void manageAuthorityCanViewAnyDocument() {
        var auth = authenticationFor("subject", "document.manage");

        assertThat(guard().canView(auth, UUID.randomUUID())).isTrue();
    }

    @Test
    void selfAuthorityGrantsViewOnlyForOwnDocument() {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        var auth = authenticationFor("subject-1", "document.read.self");

        assertThat(guard().canView(auth, self)).isTrue();
    }

    @Test
    void selfAuthorityDeniesViewForSomeoneElsesDocument() {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        var auth = authenticationFor("subject-1", "document.read.self");

        assertThat(guard().canView(auth, UUID.randomUUID())).isFalse();
    }

    @Test
    void teamAuthorityGrantsViewOnlyWhenOrganizationServiceIncludesTargetInScope() {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));
        var auth = authenticationFor("manager-subject", "document.read.team");

        assertThat(guard().canView(auth, report)).isTrue();
    }

    @Test
    void teamAuthorityDeniesViewWhenOrganizationServiceExcludesTarget() {
        UUID manager = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());
        var auth = authenticationFor("manager-subject", "document.read.team");

        assertThat(guard().canView(auth, UUID.randomUUID())).isFalse();
    }

    @Test
    void canCompleteAllowsOnlyTheUploaderOrManageOverride() {
        UUID uploader = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(uploader));
        var selfAuth = authenticationFor("uploader-subject", "document.upload.self");
        var adminAuth = authenticationFor("admin-subject", "document.manage");

        assertThat(guard().canComplete(selfAuth, uploader)).isTrue();
        assertThat(guard().canComplete(adminAuth, UUID.randomUUID())).isTrue();
    }

    @Test
    void canCompleteDeniesSomeoneOtherThanTheUploaderWithoutManageAuthority() {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        var auth = authenticationFor("subject", "document.upload.self");

        assertThat(guard().canComplete(auth, UUID.randomUUID())).isFalse();
    }
}
