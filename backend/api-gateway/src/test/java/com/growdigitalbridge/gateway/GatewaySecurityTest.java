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
}
