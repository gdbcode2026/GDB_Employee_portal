package com.growdigitalbridge.organization.api;

import com.growdigitalbridge.organization.api.dto.DepartmentDtos;
import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.config.SecurityConfig;
import com.growdigitalbridge.organization.domain.DepartmentStatus;
import com.growdigitalbridge.organization.service.DepartmentService;
import com.growdigitalbridge.organization.service.exception.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
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

@WebMvcTest(DepartmentController.class)
@Import(SecurityConfig.class)
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DepartmentService service;

    @Test
    void deniesUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/organization/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsReadWithOrganizationReadAuthority() throws Exception {
        when(service.list(any(), any(), any(Pageable.class))).thenReturn(
                new PageResponse<>(List.of(), new PageResponse.PageMeta(0, 20, 0)));

        mockMvc.perform(get("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void deniesCreateWithOnlyReadAuthority() throws Exception {
        mockMvc.perform(post("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.CreateRequest("Engineering", "ENG"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsCreateWithManageAuthority() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.create(any(), any())).thenReturn(new DepartmentDtos.Response(id, "Engineering", "ENG", DepartmentStatus.ACTIVE, now, now));

        mockMvc.perform(post("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.CreateRequest("Engineering", "ENG"))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsBlankNameWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.CreateRequest("", "ENG"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictForDuplicateCode() throws Exception {
        when(service.create(any(), any())).thenThrow(new ConflictException("Department code 'ENG' already exists."));

        mockMvc.perform(post("/api/v1/organization/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.CreateRequest("Engineering", "ENG"))))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRequiresManageAuthority() throws Exception {
        mockMvc.perform(patch("/api/v1/organization/departments/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.read")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new DepartmentDtos.UpdateRequest("New name", null, null))))
                .andExpect(status().isForbidden());
    }
}
