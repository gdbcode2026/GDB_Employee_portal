package com.growdigitalbridge.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.employee.api.dto.EmployeeDtos;
import com.growdigitalbridge.employee.client.OrganizationClient;
import com.growdigitalbridge.employee.domain.EmployeeStatus;
import com.growdigitalbridge.employee.domain.EmploymentType;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration and JPA mappings against genuine PostgreSQL, and the
 * real security filter chain, while mocking only the Organization Service dependency (a
 * separate service, not something this test should stand up). This proves self/team/all
 * visibility rules end-to-end against real data.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EmployeeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrganizationClient organizationClient;

    @Test
    void createLifecycleAndDuplicateConstraintsWorkEndToEnd() throws Exception {
        var request = new EmployeeDtos.CreateRequest("EMP-100", "Ada", "Lovelace", "ada.100@example.com", null, null,
                new EmployeeDtos.EmploymentDetails("Engineer", EmploymentType.FULL_TIME, LocalDate.now()), null);

        MvcResult created = mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        EmployeeDtos.Response employee = objectMapper.readValue(created.getResponse().getContentAsString(), EmployeeDtos.Response.class);

        mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/employees/" + employee.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EmployeeDtos.AdminUpdateRequest(null, null, null, null, EmployeeStatus.INACTIVE, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/v1/employees/" + employee.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EmployeeDtos.AdminUpdateRequest(null, null, null, null, EmployeeStatus.ACTIVE, null, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void selfEndpointResolvesFromJwtSubjectAgainstRealDatabase() throws Exception {
        var request = new EmployeeDtos.CreateRequest("EMP-200", "Grace", "Hopper", "grace.200@example.com", null, "self-subject-200",
                new EmployeeDtos.EmploymentDetails("Admiral", EmploymentType.FULL_TIME, LocalDate.now()), null);
        mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/employees/me")
                        .with(jwt().jwt(jwt -> jwt.subject("self-subject-200")).authorities(new SimpleGrantedAuthority("employee.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber").value("EMP-200"));

        mockMvc.perform(get("/api/v1/employees/me")
                        .with(jwt().jwt(jwt -> jwt.subject("no-such-subject")).authorities(new SimpleGrantedAuthority("employee.read.self"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void teamScopedReadIsAuthorizedOnlyThroughOrganizationServiceResolution() throws Exception {
        EmployeeDtos.Response manager = createEmployee("EMP-300", "Manager", "manager-subject-300");
        EmployeeDtos.Response report = createEmployee("EMP-301", "Report", null);
        EmployeeDtos.Response stranger = createEmployee("EMP-302", "Stranger", null);

        when(organizationClient.resolveTeamScope(manager.id())).thenReturn(Set.of(report.id()));

        mockMvc.perform(get("/api/v1/employees/" + report.id())
                        .with(jwt().jwt(jwt -> jwt.subject("manager-subject-300")).authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isOk());

        // Organization Service does not include "stranger" in the manager's scope, so this
        // must be denied even though the caller holds employee.read.team - proving scope
        // comes only from Organization Service, never from anything the client supplies.
        mockMvc.perform(get("/api/v1/employees/" + stranger.id())
                        .with(jwt().jwt(jwt -> jwt.subject("manager-subject-300")).authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isNotFound());
    }

    /**
     * Reporting V1 authorization review, Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md): proves
     * the exact contract the Workforce Summary report's authorization gate depends on - a
     * Manager's real, legitimate `employee.read.team` request must come back tagged "TEAM", never
     * "ALL", so the frontend's `scope === "ALL"` check correctly refuses to treat it as the
     * HR-only, organization-wide report. Exercised through the real security filter chain and a
     * real database, not a mock of the guard itself.
     *
     * <p>Also the exact call pattern (no {@code query} parameter) Workforce Summary, Team
     * Overview, and Leave Summary's employee-roster call all use - the confirmed PostgreSQL
     * parameter-typing defect in {@code EmployeeRepository.searchWithinScope} (see the
     * "PostgreSQL parameter-typing fix" verification tests below) made this throw a 500 before
     * the fix; no workaround/query parameter is used here any more.
     */
    @Test
    void listResponseTagsTeamScopeAsTeamNeverAll() throws Exception {
        EmployeeDtos.Response manager = createEmployee("EMP-400", "Manager", "manager-subject-400");
        EmployeeDtos.Response report = createEmployee("EMP-401", "Report", null);
        when(organizationClient.resolveTeamScope(manager.id())).thenReturn(Set.of(report.id()));

        mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().jwt(jwt -> jwt.subject("manager-subject-400")).authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("TEAM"));
    }

    /**
     * Same contract, the HR/unrestricted side: a true `employee.read.all` caller must get "ALL".
     * Also Workforce Summary's own exact call pattern (`GET /employees` with no {@code query}) -
     * see the class-level note above and the dedicated PostgreSQL-defect tests below.
     */
    @Test
    void listResponseTagsUnrestrictedScopeAsAll() throws Exception {
        mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    /**
     * Confirmed PostgreSQL parameter-typing defect fix (docs/REPORTING_AUTHORIZATION_REVIEW.md
     * Part A verification): {@code EmployeeRepository.searchAll}/{@code searchWithinScope}'s
     * {@code :query} parameter, referenced in a standalone {@code is null} check with no other
     * type context, was resolved by Postgres's extended query protocol as {@code bytea},
     * producing "function lower(bytea) does not exist" for any real caller omitting {@code
     * query} - i.e. every existing caller, including every Reporting V1 page. Fixed by casting
     * every occurrence of {@code :query} to an explicit type. These tests reproduce the exact
     * previously-crashing call patterns and the surrounding ones the fix must not regress.
     */
    @Test
    void listWorksWithoutAQueryParameterUnderAllScope() throws Exception {
        createEmployee("EMP-500", "NoQueryAll", null);

        mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    @Test
    void listWorksWithoutAQueryParameterUnderTeamScope() throws Exception {
        EmployeeDtos.Response manager = createEmployee("EMP-501", "Manager501", "manager-subject-501");
        EmployeeDtos.Response report = createEmployee("EMP-502", "Report502", null);
        when(organizationClient.resolveTeamScope(manager.id())).thenReturn(Set.of(report.id()));

        mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().jwt(jwt -> jwt.subject("manager-subject-501")).authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("TEAM"))
                .andExpect(jsonPath("$.items[*].employeeNumber", org.hamcrest.Matchers.hasItem("EMP-502")));
    }

    @Test
    void listWorksWithANonEmptyQueryParameterAndFiltersByIt() throws Exception {
        createEmployee("EMP-503", "Zedekiah", null);
        createEmployee("EMP-504", "Winslow", null);

        mockMvc.perform(get("/api/v1/employees?query=Zedekiah")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].firstName", org.hamcrest.Matchers.contains("Zedekiah")));
    }

    /** An explicitly-empty (not omitted) `query` must behave exactly like "no filter at all". */
    @Test
    void listTreatsAnEmptyQueryParameterTheSameAsNoFilter() throws Exception {
        createEmployee("EMP-505", "EmptyQueryCheck", null);

        MvcResult withoutQuery = mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.all"))))
                .andExpect(status().isOk()).andReturn();
        MvcResult withEmptyQuery = mockMvc.perform(get("/api/v1/employees?query=")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.all"))))
                .andExpect(status().isOk()).andReturn();

        org.assertj.core.api.Assertions.assertThat(withEmptyQuery.getResponse().getContentAsString())
                .isEqualTo(withoutQuery.getResponse().getContentAsString());
    }

    private EmployeeDtos.Response createEmployee(String employeeNumber, String firstName, String identitySubject) throws Exception {
        var request = new EmployeeDtos.CreateRequest(employeeNumber, firstName, "Test", employeeNumber.toLowerCase() + "@example.com",
                null, identitySubject, new EmployeeDtos.EmploymentDetails("Engineer", EmploymentType.FULL_TIME, LocalDate.now()), null);
        MvcResult result = mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EmployeeDtos.Response.class);
    }
}
