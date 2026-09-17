package com.growdigitalbridge.employee.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.employee.api.dto.EmployeeDtos;
import com.growdigitalbridge.employee.api.dto.PageResponse;
import com.growdigitalbridge.employee.config.SecurityConfig;
import com.growdigitalbridge.employee.domain.EmployeeStatus;
import com.growdigitalbridge.employee.domain.EmploymentType;
import com.growdigitalbridge.employee.service.EmployeeService;
import com.growdigitalbridge.employee.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeService service;

    private EmployeeDtos.Response sampleResponse(UUID id) {
        Instant now = Instant.now();
        return new EmployeeDtos.Response(id, "EMP-1", "Ada", "Lovelace", "ada@example.com", null, EmployeeStatus.ACTIVE,
                new EmployeeDtos.EmploymentSummary(UUID.randomUUID(), "Engineer", EmploymentType.FULL_TIME, LocalDate.now(), null, com.growdigitalbridge.employee.domain.EmploymentStatus.ACTIVE),
                List.of(), now, now);
    }

    @Test
    void meDeniesUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/employees/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meAllowsSelfReadAuthority() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getSelf(any())).thenReturn(sampleResponse(id));

        mockMvc.perform(get("/api/v1/employees/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.self"))))
                .andExpect(status().isOk());
    }

    @Test
    void meDeniesCallerWithoutSelfReadAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/employees/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchMeRequiresUpdateSelfAuthority() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.self")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmployeeDtos.SelfUpdateRequest("999", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByIdAllowsAnyReadAuthorityThroughSecurityGate() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(any(), any())).thenReturn(sampleResponse(id));

        mockMvc.perform(get("/api/v1/employees/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isOk());
    }

    @Test
    void getByIdReturnsNotFoundWhenServiceMasksVisibility() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(any(), any())).thenThrow(new ResourceNotFoundException("Employee " + id + " was not found."));

        mockMvc.perform(get("/api/v1/employees/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdDeniesCallerWithNoReadAuthorityAtAll() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.self"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturnsForbiddenWhenServiceDeniesScope() throws Exception {
        when(service.list(any(), any(), any(), any())).thenThrow(new AccessDeniedException("Listing employees requires team or all read scope."));

        mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.self"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllowsTeamReadAuthority() throws Exception {
        when(service.list(any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(), new PageResponse.PageMeta(0, 20, 0)));

        mockMvc.perform(get("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isOk());
    }

    @Test
    void createRequiresUpdateAllAuthority() throws Exception {
        var request = new EmployeeDtos.CreateRequest("EMP-2", "Grace", "Hopper", "grace@example.com", null, null,
                new EmployeeDtos.EmploymentDetails("Engineer", EmploymentType.FULL_TIME, LocalDate.now()), null);

        mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.self")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSucceedsWithUpdateAllAuthority() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any(), any())).thenReturn(sampleResponse(id));
        var request = new EmployeeDtos.CreateRequest("EMP-2", "Grace", "Hopper", "grace@example.com", null, null,
                new EmployeeDtos.EmploymentDetails("Engineer", EmploymentType.FULL_TIME, LocalDate.now()), null);

        mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectsBlankRequiredFieldsWithBadRequest() throws Exception {
        var request = new EmployeeDtos.CreateRequest("", "", "Hopper", "not-an-email", null, null, null, null);

        mockMvc.perform(post("/api/v1/employees")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.update.all")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchByIdRequiresUpdateAllAuthority() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.all")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmployeeDtos.AdminUpdateRequest("New", null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }
}
