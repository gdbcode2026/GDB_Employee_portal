package com.growdigitalbridge.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {
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
}
