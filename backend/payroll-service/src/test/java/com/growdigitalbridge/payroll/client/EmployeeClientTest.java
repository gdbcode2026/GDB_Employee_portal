package com.growdigitalbridge.payroll.client;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * Covers the Payroll V1 terminated-employee eligibility fix (GDB business decision Option A) at
 * the {@link EmployeeClient} level: {@link EmployeeClient#resolveEmployeeRefsEligibleForPeriod}
 * must include every {@code ACTIVE} employee plus any {@code INACTIVE} employee whose employment
 * dates overlapped the payroll period, using only Employee Service's existing endpoints.
 */
class EmployeeClientTest {

    private static final String BASE_URL = "http://employee-service";

    private MockRestServiceServer server;
    private EmployeeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EmployeeClient(builder.build());

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("hr-1")
                .claim("sub", "hr-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void expectActivePage(UUID... ids) {
        server.expect(requestTo(BASE_URL + "/api/v1/employees?status=ACTIVE&page=0&size=200"))
                .andExpect(method(GET))
                .andRespond(withSuccess(activePageJson(ids), MediaType.APPLICATION_JSON));
    }

    private void expectInactivePage(UUID... ids) {
        server.expect(requestTo(BASE_URL + "/api/v1/employees?status=INACTIVE&page=0&size=200"))
                .andExpect(method(GET))
                .andRespond(withSuccess(inactivePageJson(ids), MediaType.APPLICATION_JSON));
    }

    private void expectEmploymentDetail(UUID id, String startDate, String endDate) {
        server.expect(requestTo(BASE_URL + "/api/v1/employees/" + id))
                .andExpect(method(GET))
                .andRespond(withSuccess(profileJson(id, startDate, endDate), MediaType.APPLICATION_JSON));
    }

    private static String activePageJson(UUID... ids) {
        return pageJson("ACTIVE", ids);
    }

    private static String inactivePageJson(UUID... ids) {
        return pageJson("INACTIVE", ids);
    }

    private static String pageJson(String status, UUID... ids) {
        StringBuilder items = new StringBuilder();
        for (UUID id : ids) {
            if (items.length() > 0) {
                items.append(',');
            }
            items.append("{\"id\":\"").append(id).append("\",\"status\":\"").append(status).append("\"}");
        }
        return "{\"items\":[" + items + "],\"page\":{\"number\":0,\"size\":200,\"total\":" + ids.length + "}}";
    }

    private static String profileJson(UUID id, String startDate, String endDate) {
        String endDateJson = endDate == null ? "null" : "\"" + endDate + "\"";
        return "{\"id\":\"" + id + "\",\"employeeNumber\":\"E1\",\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                + "\"employment\":{\"jobTitle\":\"Engineer\",\"startDate\":\"" + startDate + "\",\"endDate\":" + endDateJson + "}}";
    }

    @Test
    void activeEmployeeThroughoutTheCompletePeriodIsIncluded() {
        UUID activeId = UUID.randomUUID();
        expectActivePage(activeId);
        expectInactivePage();

        Set<UUID> eligible = client.resolveEmployeeRefsEligibleForPeriod(of(2026, 10, 1), of(2026, 10, 31));

        assertThat(eligible).containsExactly(activeId);
        server.verify();
    }

    @Test
    void employeeWhoTerminatesDuringThePeriodIsIncluded() {
        UUID terminatedId = UUID.randomUUID();
        expectActivePage();
        expectInactivePage(terminatedId);
        expectEmploymentDetail(terminatedId, "2026-09-01", "2026-10-15");

        Set<UUID> eligible = client.resolveEmployeeRefsEligibleForPeriod(of(2026, 10, 1), of(2026, 10, 31));

        assertThat(eligible).containsExactly(terminatedId);
        server.verify();
    }

    @Test
    void employeeWhoTerminatesExactlyOnThePeriodStartIsIncluded() {
        UUID terminatedId = UUID.randomUUID();
        expectActivePage();
        expectInactivePage(terminatedId);
        expectEmploymentDetail(terminatedId, "2026-09-01", "2026-10-01");

        Set<UUID> eligible = client.resolveEmployeeRefsEligibleForPeriod(of(2026, 10, 1), of(2026, 10, 31));

        assertThat(eligible).containsExactly(terminatedId);
        server.verify();
    }

    @Test
    void employeeWhoTerminatesExactlyBeforeThePeriodStartIsExcluded() {
        UUID terminatedId = UUID.randomUUID();
        expectActivePage();
        expectInactivePage(terminatedId);
        expectEmploymentDetail(terminatedId, "2026-08-01", "2026-09-30");

        Set<UUID> eligible = client.resolveEmployeeRefsEligibleForPeriod(of(2026, 10, 1), of(2026, 10, 31));

        assertThat(eligible).isEmpty();
        server.verify();
    }

    @Test
    void employeeHiredAfterThePeriodEndIsExcluded() {
        UUID candidateId = UUID.randomUUID();
        expectActivePage();
        expectInactivePage(candidateId);
        expectEmploymentDetail(candidateId, "2026-11-01", null);

        Set<UUID> eligible = client.resolveEmployeeRefsEligibleForPeriod(of(2026, 10, 1), of(2026, 10, 31));

        assertThat(eligible).isEmpty();
        server.verify();
    }

    @Test
    void employeeHiredDuringThePeriodIsIncludedWhenSupportedByExistingEmploymentDates() {
        UUID candidateId = UUID.randomUUID();
        expectActivePage();
        expectInactivePage(candidateId);
        expectEmploymentDetail(candidateId, "2026-10-20", null);

        Set<UUID> eligible = client.resolveEmployeeRefsEligibleForPeriod(of(2026, 10, 1), of(2026, 10, 31));

        assertThat(eligible).containsExactly(candidateId);
        server.verify();
    }

    @Test
    void existingActiveEmployeeResolutionBehaviorIsUnchanged() {
        UUID activeId = UUID.randomUUID();
        expectActivePage(activeId);

        Set<UUID> active = client.resolveActiveEmployeeRefs();

        assertThat(active).containsExactly(activeId);
        server.verify();
    }
}
