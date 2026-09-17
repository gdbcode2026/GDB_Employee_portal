package com.growdigitalbridge.organization.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.organization.api.dto.ReportingRelationDtos;
import com.growdigitalbridge.organization.config.SecurityConfig;
import com.growdigitalbridge.organization.domain.ReportingRelationStatus;
import com.growdigitalbridge.organization.service.ReportingRelationService;
import com.growdigitalbridge.organization.service.exception.InvalidReportingRelationException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingRelationController.class)
@Import(SecurityConfig.class)
class ReportingRelationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportingRelationService service;

    @Test
    void scopeEndpointDeniesUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/organization/reporting-relations/scope/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopeEndpointResolvesFromPathManagerRefOnly() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        when(service.resolveScope(manager)).thenReturn(new ReportingRelationDtos.ScopeResponse(manager, List.of(report)));

        mockMvc.perform(get("/api/v1/organization/reporting-relations/scope/" + manager)
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerEmployeeRef").value(manager.toString()))
                .andExpect(jsonPath("$.employeeRefs[0]").value(report.toString()));
    }

    @Test
    void deniesCreateWithoutManageAuthority() throws Exception {
        var request = new ReportingRelationDtos.CreateRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
        mockMvc.perform(post("/api/v1/organization/reporting-relations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsUnprocessableEntityForCycleViolation() throws Exception {
        when(service.create(any(), any())).thenThrow(new InvalidReportingRelationException("Assigning this manager would create a reporting-hierarchy cycle."));
        var request = new ReportingRelationDtos.CreateRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());

        mockMvc.perform(post("/api/v1/organization/reporting-relations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }
}
