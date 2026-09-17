package com.growdigitalbridge.organization.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.organization.api.dto.TeamDtos;
import com.growdigitalbridge.organization.config.SecurityConfig;
import com.growdigitalbridge.organization.domain.TeamStatus;
import com.growdigitalbridge.organization.service.TeamService;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
@Import(SecurityConfig.class)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TeamService service;

    @Test
    void deniesUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/organization/teams")).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesCreateWithoutManageAuthority() throws Exception {
        mockMvc.perform(post("/api/v1/organization/teams")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamDtos.CreateRequest(UUID.randomUUID(), "Platform", "PLAT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsCreateWithManageAuthority() throws Exception {
        UUID departmentId = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.create(any(), any())).thenReturn(
                new TeamDtos.Response(UUID.randomUUID(), departmentId, "Platform", "PLAT", TeamStatus.ACTIVE, now, now));

        mockMvc.perform(post("/api/v1/organization/teams")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamDtos.CreateRequest(departmentId, "Platform", "PLAT"))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMissingDepartmentIdWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/organization/teams")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamDtos.CreateRequest(null, "Platform", "PLAT"))))
                .andExpect(status().isBadRequest());
    }
}
