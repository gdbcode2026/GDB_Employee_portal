package com.growdigitalbridge.employee.config;

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
 * Deny-by-default resource server, identical posture to Organization Service: no
 * {@link JwtDecoder} bean exists until GDB supplies an OIDC issuer, so every protected
 * endpoint is rejected with 401 today - no bypass or placeholder authentication.
 *
 * Path rules grant entry using the platform RBAC capabilities only
 * ({@code employee.read.self/team/all}, {@code employee.update.self/all}). Fine-grained
 * self/team visibility (is this specific employee visible to this caller?) is enforced in
 * {@code EmployeeAccessGuard}, which resolves team scope by calling Organization Service -
 * never from a client-supplied identifier.
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/employees/me").hasAuthority("employee.read.self")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/employees/me").hasAuthority("employee.update.self")
                        .requestMatchers(HttpMethod.POST, "/api/v1/employees").hasAuthority("employee.update.all")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/employees/*").hasAuthority("employee.update.all")
                        .requestMatchers(HttpMethod.GET, "/api/v1/employees/**")
                        .hasAnyAuthority("employee.read.self", "employee.read.team", "employee.read.all")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty("gdb.security.oidc.issuer-uri")
    JwtDecoder jwtDecoder(EmployeeOidcProperties properties) {
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
