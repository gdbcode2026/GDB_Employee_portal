package com.growdigitalbridge.gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Production Authentication Phase 1 review found {@code SecurityConfig}'s terminal
 * {@code .anyExchange().denyAll()} denied every proxied route unconditionally, even for a fully
 * valid, correctly-signed JWT - contradicting the documented "gateway authenticates, each service
 * authorizes" design (docs/DECISIONS.md). Fixed to {@code .anyExchange().authenticated()}.
 *
 * <p>{@code STUB} is a minimal JDK-only (no new test dependency) downstream double, standing in
 * for the audit-service route only, so {@link #authenticatedRequestIsRoutedPastTheGatewayToTheDownstreamStub()}
 * below can prove the gateway actually forwards an authenticated request - not just that it
 * fails to 401/403 it. The other 12 routes are untouched and still point at their real (offline
 * in this test) default URIs, exactly as before this fix. {@code jwtDecoder} is mocked rather
 * than configuring a real issuer/JWKS, matching this task's "no OIDC provider" constraint - it
 * proves the gateway's authorization rule, not real signature verification (every service's own
 * {@code SecurityConfig} already covers that independently).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static HttpServer STUB;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @BeforeAll
    static void startDownstreamStub() throws Exception {
        STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        STUB.createContext("/", exchange -> {
            byte[] body = "downstream-ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        STUB.start();
    }

    @AfterAll
    static void stopDownstreamStub() {
        STUB.stop(0);
    }

    @DynamicPropertySource
    static void overrideAuditServiceRouteToTheStub(DynamicPropertyRegistry registry) {
        registry.add("GDB_GATEWAY_AUDIT_SERVICE_URI", () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @LocalServerPort int port;
    @Test void returnsUnauthorizedForUnauthenticatedApiRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/audit/events")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedOrganizationRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/organization/departments")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedEmployeeRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/employees/me")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedAttendanceRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/attendance/me")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedLeaveRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/leave/requests")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedProjectRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/projects")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedTaskRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/tasks/me")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedPerformanceRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/performance/goals")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedDocumentRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/policies")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedWorkflowRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/workflows/tasks/me")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedAssetRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/assets/me")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedExpenseRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/expenses/claims")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    @Test void returnsUnauthorizedForUnauthenticatedPayrollRoute() {
        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/payroll/runs")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();
        assertThat(status).isEqualTo(401);
    }

    /**
     * Proves the fix: a request bearing a valid JWT is no longer unconditionally denied at the
     * gateway - it is authenticated and forwarded to the downstream route (the audit-service
     * route, redirected to {@code STUB} above for this test only), reaching the stub's 200
     * response rather than being rejected with 401/403 by the gateway's own security chain. This
     * does NOT assert the gateway grants any specific authority - that decision remains each
     * domain service's own, unchanged by this fix.
     */
    @Test void authenticatedRequestIsRoutedPastTheGatewayToTheDownstreamStub() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1")
                .claim("sub", "user-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        int status = WebClient.create("http://localhost:" + port).get().uri("/api/v1/audit/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer any-token-value")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value())).block();

        assertThat(status).isEqualTo(200);
    }
}
