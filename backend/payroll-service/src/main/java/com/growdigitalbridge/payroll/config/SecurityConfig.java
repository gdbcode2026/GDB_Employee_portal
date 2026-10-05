package com.growdigitalbridge.payroll.config;

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
 * Deny-by-default resource server, identical posture to every service in this platform: no
 * {@link JwtDecoder} bean exists until GDB supplies an OIDC issuer, so every protected endpoint
 * is rejected with 401 today - no bypass or placeholder authentication.
 *
 * Path rules grant entry using only the documented RBAC.md/PAYROLL_REQUIREMENTS.md capabilities:
 * {@code payroll.read.all}, {@code payroll.process}, {@code payroll.approve}, and now {@code
 * payslip.read.self}/{@code payslip.read.all} (Section O/P, payslip generation phase). {@code
 * payroll.read.self} still gates nothing - Section P documents it without ever assigning it an
 * endpoint. The payslip path rules only admit the request; {@code PayslipService}/{@code
 * DocumentAccessGuard}-equivalent logic still decides, per resource, whether a {@code
 * payslip.read.self}-only caller may see the specific payslip requested (their own only, server-
 * side, never from a client-supplied identity).
 *
 * Period read access (no dedicated permission is documented for periods at all) is granted to
 * any of the three payroll-authorized capabilities, since HR/Finance staff performing any
 * payroll action plausibly need to see which periods exist - a minimum decision, not an
 * additional grant beyond what a payroll-authorized identity already holds.
 *
 * Compensation Management/Statutory Profile/Pay Component/Exception endpoints (Compensation
 * Management task) reuse only the already-documented {@code payroll.process}/{@code
 * payroll.read.all} - no new HR-vs-Finance split permission is introduced (that split remains
 * PENDING_GDB_APPROVAL, Section X). Writes require {@code payroll.process}; reads accept either
 * {@code payroll.process} or {@code payroll.read.all}. There is no self-service read or write
 * path for any of these - an employee token, however many {@code payroll.read.self}/{@code
 * payslip.*} authorities it carries, has none of the authorities these matchers require and is
 * therefore denied. Admin is never granted {@code payroll.*} automatically by this configuration.
 *
 * {@code payroll.process} is the maker side (create period, create/process a run, initiate an
 * adjustment run against a FINALIZED one - Section K); {@code payroll.approve} is the checker
 * side (approve/reject/finalize a run) - see PAYROLL_REQUIREMENTS.md Section J. Self-approval
 * prevention (the same identity cannot approve/reject/finalize a run it initiated) is enforced
 * in {@code PayrollRunService}, not here, since it depends on the specific run's recorded
 * {@code initiatedBy}, not just the caller's grants - this applies identically to an adjustment
 * run, since nothing in that service branches on {@code runType}.
 *
 * <p>Statutory/tax rule configuration (Rule Engine task) reuses the identical maker-checker
 * split: creating or editing a {@code DRAFT} rule version is {@code payroll.process};
 * activating/deactivating a version - making it live for calculation, or taking it out of
 * service - is {@code payroll.approve}. No new permission is introduced; the HR-vs-Finance split
 * remains PENDING_GDB_APPROVAL (Section X) exactly as for every other Compensation Management
 * endpoint above.
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/periods").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/periods", "/api/v1/payroll/periods/*")
                        .hasAnyAuthority("payroll.process", "payroll.read.all", "payroll.approve")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/process").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/submit").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/approve").hasAuthority("payroll.approve")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/reject").hasAuthority("payroll.approve")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/finalize").hasAuthority("payroll.approve")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/adjustments").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/runs", "/api/v1/payroll/runs/*").hasAuthority("payroll.read.all")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/compensations").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/payroll/compensations/*").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/compensations", "/api/v1/payroll/compensations/*")
                        .hasAnyAuthority("payroll.process", "payroll.read.all")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/pay-components").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/payroll/pay-components/*").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/pay-components", "/api/v1/payroll/pay-components/*")
                        .hasAnyAuthority("payroll.process", "payroll.read.all")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/payroll/statutory-profiles/*").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/statutory-profiles/*")
                        .hasAnyAuthority("payroll.process", "payroll.read.all")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/exceptions/*/resolve").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/exceptions")
                        .hasAnyAuthority("payroll.process", "payroll.read.all")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/statutory-rules").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/payroll/statutory-rules/*").hasAuthority("payroll.process")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payroll/statutory-rules/*/activate",
                                "/api/v1/payroll/statutory-rules/*/deactivate").hasAuthority("payroll.approve")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/statutory-rules", "/api/v1/payroll/statutory-rules/*")
                        .hasAnyAuthority("payroll.process", "payroll.read.all", "payroll.approve")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/payslips/me")
                        .hasAnyAuthority("payslip.read.self", "payslip.read.all")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/payslips/*/download")
                        .hasAnyAuthority("payslip.read.self", "payslip.read.all")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payroll/payslips/*")
                        .hasAnyAuthority("payslip.read.self", "payslip.read.all")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty("gdb.security.oidc.issuer-uri")
    JwtDecoder jwtDecoder(PayrollOidcProperties properties) {
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
