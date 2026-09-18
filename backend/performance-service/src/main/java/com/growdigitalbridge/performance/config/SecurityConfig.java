package com.growdigitalbridge.performance.config;

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
 * {@code performance.read.self/team/all}, {@code performance.goal.manage.self},
 * {@code performance.review.submit.team}, {@code performance.manage}. Fine-grained
 * self/team/reviewer visibility is enforced in {@code PerformanceAccessGuard}, which
 * resolves team scope by calling Organization Service and self by calling Employee Service -
 * never from a client-supplied identifier. RBAC.md defines no distinct self-review or
 * goal-review-team permission, so review creation/submission is gated by the sole documented
 * {@code performance.review.submit.team} capability, with the resource-scope check (is the
 * caller the assigned reviewer?) doing the real authorization work - see PerformanceAccessGuard.
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/performance/goals")
                        .hasAnyAuthority("performance.read.self", "performance.read.team", "performance.read.all")
                        .requestMatchers(HttpMethod.POST, "/api/v1/performance/goals").hasAuthority("performance.goal.manage.self")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/performance/goals/*").hasAuthority("performance.goal.manage.self")
                        .requestMatchers(HttpMethod.GET, "/api/v1/performance/cycles")
                        .hasAnyAuthority("performance.read.self", "performance.read.team", "performance.read.all", "performance.manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/performance/cycles").hasAuthority("performance.manage")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/performance/cycles/*").hasAuthority("performance.manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/performance/reviews/*/submit")
                        .hasAnyAuthority("performance.review.submit.team", "performance.manage")
                        .requestMatchers(HttpMethod.POST, "/api/v1/performance/reviews")
                        .hasAnyAuthority("performance.review.submit.team", "performance.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/performance/reviews")
                        .hasAnyAuthority("performance.read.self", "performance.read.team", "performance.read.all")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty("gdb.security.oidc.issuer-uri")
    JwtDecoder jwtDecoder(PerformanceOidcProperties properties) {
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
