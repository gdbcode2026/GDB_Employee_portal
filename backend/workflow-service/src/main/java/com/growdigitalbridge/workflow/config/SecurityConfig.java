package com.growdigitalbridge.workflow.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Deny-by-default resource server, identical posture to every other platform service: no
 * {@link JwtDecoder} bean exists until GDB supplies an OIDC issuer, so every protected
 * endpoint is rejected with 401 today - no bypass or placeholder authentication.
 *
 * Path rules grant entry using only the documented RBAC.md capabilities:
 * {@code workflow.read.self/team/all}, {@code workflow.decide.assigned}, {@code
 * workflow.manage}. API.md's own Workflows row groups both the decision and cancel actions
 * under the single {@code workflow.decide.assigned} permission, so cancel is gated the same
 * way here (with {@code workflow.manage} as an administrative override) - see
 * WorkflowAccessGuard for the resource-level "is this caller actually allowed to cancel this
 * instance" check. Definition/instance-creation endpoints are a flagged minimum addition (see
 * WorkflowService) with no dedicated permission documented, so they reuse {@code
 * workflow.manage} - the platform's established flat "administrative" permission shape.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectProvider<JwtDecoder> decoder) throws Exception {
        decoder.ifAvailable(value -> {
            try {
                http.oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.decoder(value).jwtAuthenticationConverter(authenticationConverter())));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure JWT resource server", e);
            }
        });
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflows/tasks/me").hasAuthority("workflow.read.self")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/definitions").hasAuthority("workflow.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflows/definitions").hasAuthority("workflow.manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/tasks/*/decisions").hasAuthority("workflow.decide.assigned")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/tasks/*/delegations").hasAuthority("workflow.decide.assigned")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/cancel")
                        .hasAnyAuthority("workflow.decide.assigned", "workflow.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflows/*")
                        .hasAnyAuthority("workflow.read.self", "workflow.read.team", "workflow.read.all")
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflows")
                        .hasAnyAuthority("workflow.read.self", "workflow.read.team", "workflow.read.all")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows").hasAuthority("workflow.manage")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty("gdb.security.oidc.issuer-uri")
    JwtDecoder jwtDecoder(WorkflowOidcProperties properties) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuerUri()), audience));
        return decoder;
    }

    private JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            Object permissions = jwt.getClaim("permissions");
            if (permissions instanceof Iterable<?> values) {
                values.forEach(value -> authorities.add(new SimpleGrantedAuthority(String.valueOf(value))));
            }
            Object roles = jwt.getClaim("roles");
            if (roles instanceof Iterable<?> values) {
                values.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }
}
