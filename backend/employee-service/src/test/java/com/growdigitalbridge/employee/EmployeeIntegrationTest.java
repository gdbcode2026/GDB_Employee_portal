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
