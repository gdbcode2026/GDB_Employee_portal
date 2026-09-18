package com.growdigitalbridge.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.project.api.dto.ProjectDtos;
import com.growdigitalbridge.project.api.dto.ProjectMembershipDtos;
import com.growdigitalbridge.project.api.dto.TaskDtos;
import com.growdigitalbridge.project.client.EmployeeClient;
import com.growdigitalbridge.project.client.OrganizationClient;
import com.growdigitalbridge.project.domain.MembershipRole;
import com.growdigitalbridge.project.domain.MembershipStatus;
import com.growdigitalbridge.project.domain.TaskPriority;
import com.growdigitalbridge.project.domain.TaskStatus;
import java.util.List;
import java.util.Optional;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration and JPA mappings against genuine PostgreSQL, and the
 * real security filter chain, while mocking only Employee/Organization Service (separate
 * services, not something this test should stand up).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ProjectIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeClient employeeClient;

    @MockitoBean
    private OrganizationClient organizationClient;

    private ProjectDtos.Response createProject(UUID ownerId, String code) throws Exception {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(ownerId));
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectDtos.CreateRequest(code, "Project " + code))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ProjectDtos.Response.class);
    }

    @Test
    void creatingAProjectAutoEnrolsTheOwnerAsAnActiveLeadMember() throws Exception {
        UUID owner = UUID.randomUUID();
        ProjectDtos.Response project = createProject(owner, "PRJ-100");

        MvcResult result = mockMvc.perform(get("/api/v1/projects/" + project.id() + "/members")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage"))))
                .andExpect(status().isOk()).andReturn();
        List<ProjectMembershipDtos.Response> members = objectMapper.readValue(result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProjectMembershipDtos.Response.class));

        org.assertj.core.api.Assertions.assertThat(members).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(members.get(0).employeeRef()).isEqualTo(owner);
        org.assertj.core.api.Assertions.assertThat(members.get(0).role()).isEqualTo(MembershipRole.LEAD);
    }

    @Test
    void duplicateProjectCodeIsRejectedWithConflict() throws Exception {
        UUID owner = UUID.randomUUID();
        createProject(owner, "PRJ-DUP");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        mockMvc.perform(post("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectDtos.CreateRequest("PRJ-DUP", "Another Name"))))
                .andExpect(status().isConflict());
    }

    @Test
    void projectReadVisibilityIsMembershipScopedNotClientSupplied() throws Exception {
        UUID owner = UUID.randomUUID();
        ProjectDtos.Response project = createProject(owner, "PRJ-200");

        UUID outsider = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(outsider));

        // An outsider with plain project.read - not a member - cannot see this project.
        mockMvc.perform(get("/api/v1/projects/" + project.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.read"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        // The owner (an active member) can see it.
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        mockMvc.perform(get("/api/v1/projects/" + project.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void membershipAddRemoveReactivateLifecycleWorks() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        ProjectDtos.Response project = createProject(owner, "PRJ-300");

        MvcResult added = mockMvc.perform(post("/api/v1/projects/" + project.id() + "/members")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectMembershipDtos.AddRequest(employee, MembershipRole.MEMBER))))
                .andExpect(status().isCreated()).andReturn();
        ProjectMembershipDtos.Response membership = objectMapper.readValue(added.getResponse().getContentAsString(), ProjectMembershipDtos.Response.class);

        // Adding the same active member again conflicts.
        mockMvc.perform(post("/api/v1/projects/" + project.id() + "/members")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectMembershipDtos.AddRequest(employee, MembershipRole.MEMBER))))
                .andExpect(status().isConflict());

        // Remove, then reactivate.
        mockMvc.perform(patch("/api/v1/projects/" + project.id() + "/members/" + membership.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectMembershipDtos.UpdateRequest(null, MembershipStatus.REMOVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        mockMvc.perform(patch("/api/v1/projects/" + project.id() + "/members/" + membership.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectMembershipDtos.UpdateRequest(MembershipRole.LEAD, MembershipStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("LEAD"));
    }

    @Test
    void taskCreationValidatesAssigneeIsAnActiveProjectMember() throws Exception {
        UUID owner = UUID.randomUUID();
        ProjectDtos.Response project = createProject(owner, "PRJ-400");
        UUID stranger = UUID.randomUUID();

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        mockMvc.perform(post("/api/v1/projects/" + project.id() + "/tasks")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDtos.CreateRequest(stranger, "Do the thing", null, null, null))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/projects/" + project.id() + "/tasks")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDtos.CreateRequest(owner, "Do the thing", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void selfScopedTaskUpdateIsRestrictedToStatusAndPriority() throws Exception {
        UUID owner = UUID.randomUUID();
        ProjectDtos.Response project = createProject(owner, "PRJ-500");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        MvcResult created = mockMvc.perform(post("/api/v1/projects/" + project.id() + "/tasks")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDtos.CreateRequest(owner, "My task", null, TaskPriority.LOW, null))))
                .andExpect(status().isCreated()).andReturn();
        TaskDtos.Response task = objectMapper.readValue(created.getResponse().getContentAsString(), TaskDtos.Response.class);

        // Self can change status/priority on their own task.
        mockMvc.perform(patch("/api/v1/tasks/" + task.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("task.manage.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDtos.UpdateRequest(null, null, null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        // Self cannot retitle their own task - that requires team/project.manage authority.
        mockMvc.perform(patch("/api/v1/tasks/" + task.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("task.manage.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDtos.UpdateRequest(null, "New title", null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamScopedTaskListingIsAuthorizedOnlyThroughOrganizationServiceResolution() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        ProjectDtos.Response project = createProject(owner, "PRJ-600");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        mockMvc.perform(post("/api/v1/projects/" + project.id() + "/members")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectMembershipDtos.AddRequest(report, MembershipRole.MEMBER))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/" + project.id() + "/tasks")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDtos.CreateRequest(report, "Report's task", null, null, null))))
                .andExpect(status().isCreated());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/tasks").with(jwt().authorities(new SimpleGrantedAuthority("task.manage.team"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(report));

        mockMvc.perform(get("/api/v1/tasks").with(jwt().authorities(new SimpleGrantedAuthority("task.manage.team"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));
    }
}
