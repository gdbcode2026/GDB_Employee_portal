package com.growdigitalbridge.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.organization.api.dto.DepartmentDtos;
import com.growdigitalbridge.organization.api.dto.ReportingRelationDtos;
import com.growdigitalbridge.organization.api.dto.TeamDtos;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real Flyway migration and JPA mappings against a genuine PostgreSQL instance,
 * verifying the partial-unique-index, cycle-protection, and recursive scope-resolution
 * behavior that a mocked repository cannot prove.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class OrganizationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void departmentAndTeamLifecycleWorksEndToEnd() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.CreateRequest("Engineering", "ENG"))))
                .andExpect(status().isCreated())
                .andReturn();
        DepartmentDtos.Response department = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), DepartmentDtos.Response.class);

        mockMvc.perform(post("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.CreateRequest("Engineering Again", "ENG"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/organization/teams")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TeamDtos.CreateRequest(department.id(), "Platform", "PLAT"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organization/chart")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0].code").value("ENG"))
                .andExpect(jsonPath("$.departments[0].teams[0].code").value("PLAT"));
    }

    @Test
    void reportingScopeAndCycleProtectionHoldAgainstRealDatabase() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        createRelation(b, a, LocalDate.now().minusDays(2));
        createRelation(c, b, LocalDate.now().minusDays(1));

        mockMvc.perform(get("/api/v1/organization/reporting-relations/scope/" + a)
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeRefs", containsInAnyOrder(b.toString(), c.toString())));

        // a reporting to c would close the loop a -> b -> c -> a.
        mockMvc.perform(post("/api/v1/organization/reporting-relations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportingRelationDtos.CreateRequest(a, c, LocalDate.now()))))
                .andExpect(status().isUnprocessableEntity());

        // b already has an active manager (a); a second active assignment must be rejected.
        mockMvc.perform(post("/api/v1/organization/reporting-relations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportingRelationDtos.CreateRequest(b, c, LocalDate.now()))))
                .andExpect(status().isConflict());
    }

    private void createRelation(UUID employeeRef, UUID managerRef, LocalDate start) throws Exception {
        mockMvc.perform(post("/api/v1/organization/reporting-relations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportingRelationDtos.CreateRequest(employeeRef, managerRef, start))))
                .andExpect(status().isCreated());
    }
}
