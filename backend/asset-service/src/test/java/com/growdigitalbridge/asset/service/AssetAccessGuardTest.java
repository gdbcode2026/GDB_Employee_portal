package com.growdigitalbridge.asset.service;

import com.growdigitalbridge.asset.client.EmployeeClient;
import java.time.Instant;
import java.util.Optional;
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
class AssetAccessGuardTest {

    @Mock
    private EmployeeClient employeeClient;

    private AssetAccessGuard guard() {
        return new AssetAccessGuard(employeeClient);
    }

    private JwtAuthenticationToken authenticationFor(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("sub", subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(jwt, granted);
    }

    @Test
    void resolveSelfDelegatesToEmployeeClient() {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));

        assertThat(guard().resolveSelf(authenticationFor("subject")).orElseThrow()).isEqualTo(self);
    }

    @Test
    void hasAuthorityChecksGrantedAuthoritiesOnly() {
        var auth = authenticationFor("subject", "asset.manage");

        assertThat(guard().hasAuthority(auth, "asset.manage")).isTrue();
        assertThat(guard().hasAuthority(auth, "asset.assign")).isFalse();
    }
}
