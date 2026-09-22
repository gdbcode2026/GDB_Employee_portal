package com.growdigitalbridge.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.workflow.api.dto.ApprovalTaskDtos;
import com.growdigitalbridge.workflow.api.dto.DelegationDtos;
import com.growdigitalbridge.workflow.api.dto.WorkflowDefinitionDtos;
import com.growdigitalbridge.workflow.api.dto.WorkflowInstanceDtos;
import com.growdigitalbridge.workflow.client.EmployeeClient;
import com.growdigitalbridge.workflow.client.OrganizationClient;
import com.growdigitalbridge.workflow.domain.Decision;
import com.growdigitalbridge.workflow.domain.RequestType;
import java.time.Instant;
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
class WorkflowIntegrationTest {

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

    private UUID createDefinition(RequestType type, int version) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkflowDefinitionDtos.CreateRequest(type, version, java.util.Map.of("stage", "single")))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowDefinitionDtos.Response.class).id();
    }

    private WorkflowInstanceDtos.Response startInstance(UUID definitionId, UUID requester, List<UUID> assignees) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkflowInstanceDtos.StartRequest(definitionId, RequestType.LEAVE, UUID.randomUUID(), requester, assignees, null))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowInstanceDtos.Response.class);
    }

    @Test
    void duplicateDefinitionVersionConflicts() throws Exception {
        createDefinition(RequestType.EXPENSE, 1);

        mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkflowDefinitionDtos.CreateRequest(RequestType.EXPENSE, 1, java.util.Map.of()))))
                .andExpect(status().isConflict());
    }

    @Test
    void instanceApprovesOnlyOnceEveryTaskIsApproved() throws Exception {
        UUID definitionId = createDefinition(RequestType.LEAVE, 1);
        UUID requester = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        WorkflowInstanceDtos.Response instance = startInstance(definitionId, requester, List.of(approver1, approver2));
        UUID task1 = instance.tasks().get(0).id();
        UUID task2 = instance.tasks().get(1).id();

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(approver1));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + task1 + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovalTaskDtos.DecisionRequest(Decision.APPROVED, "looks good"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"));

        // Instance still RUNNING - the second task hasn't been decided yet.
        MvcResult afterFirst = mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.all"))))
                .andExpect(status().isOk()).andReturn();
        WorkflowInstanceDtos.Response afterFirstResponse = objectMapper.readValue(afterFirst.getResponse().getContentAsString(), WorkflowInstanceDtos.Response.class);
        org.assertj.core.api.Assertions.assertThat(afterFirstResponse.status().name()).isEqualTo("RUNNING");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(approver2));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + task2 + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovalTaskDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Already decided - cannot decide again.
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + task2 + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovalTaskDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectionEndsTheInstanceAndCancelsRemainingTasks() throws Exception {
        UUID definitionId = createDefinition(RequestType.EXPENSE, 2);
        UUID requester = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        WorkflowInstanceDtos.Response instance = startInstance(definitionId, requester, List.of(approver1, approver2));
        UUID task1 = instance.tasks().get(0).id();

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(approver1));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + task1 + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovalTaskDtos.DecisionRequest(Decision.REJECTED, "no budget"))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andReturn();
        WorkflowInstanceDtos.Response rejected = objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowInstanceDtos.Response.class);
        org.assertj.core.api.Assertions.assertThat(rejected.tasks().get(1).status().name()).isEqualTo("CANCELLED");
    }

    @Test
    void onlyTheRequesterOrWorkflowManageMayCancel() throws Exception {
        UUID definitionId = createDefinition(RequestType.WFH, 1);
        UUID requester = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        WorkflowInstanceDtos.Response instance = startInstance(definitionId, requester, List.of(approver));

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned"))))
                .andExpect(status().isForbidden());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(requester));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void visibilityIsAuthorizedOnlyThroughDocumentedSelfTeamAllScopes() throws Exception {
        UUID definitionId = createDefinition(RequestType.ASSET_REQUEST, 1);
        UUID requester = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        WorkflowInstanceDtos.Response instance = startInstance(definitionId, requester, List.of(approver));

        UUID stranger = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(stranger));
        mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.self"))))
                .andExpect(status().isNotFound());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(requester));
        mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.self"))))
                .andExpect(status().isOk());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());
        mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.team"))))
                .andExpect(status().isNotFound());

        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(requester));
        mockMvc.perform(get("/api/v1/workflows/" + instance.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.team"))))
                .andExpect(status().isOk());
    }

    @Test
    void delegationTransfersDecisionAuthorityForABoundedPeriodWithoutRemovingTheOriginalAssignee() throws Exception {
        UUID definitionId = createDefinition(RequestType.DOCUMENT_REQUEST, 1);
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        UUID delegate = UUID.randomUUID();
        WorkflowInstanceDtos.Response instance = startInstance(definitionId, requester, List.of(assignee));
        UUID taskId = instance.tasks().get(0).id();

        // Only the original assignee may create the delegation.
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + taskId + "/delegations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DelegationDtos.CreateRequest(delegate, null, Instant.now().plusSeconds(3600)))))
                .andExpect(status().isNotFound());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(assignee));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + taskId + "/delegations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DelegationDtos.CreateRequest(delegate, null, Instant.now().plusSeconds(3600)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // A second concurrent delegation on the same task conflicts.
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + taskId + "/delegations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DelegationDtos.CreateRequest(UUID.randomUUID(), null, Instant.now().plusSeconds(3600)))))
                .andExpect(status().isConflict());

        // The delegate can now decide the task on the original assignee's behalf.
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(delegate));
        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/tasks/" + taskId + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovalTaskDtos.DecisionRequest(Decision.APPROVED, "on behalf"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"));
    }

    @Test
    void tasksMeReturnsOnlyTheCallersOwnPendingTasks() throws Exception {
        UUID definitionId = createDefinition(RequestType.ATTENDANCE_REGULARIZATION, 1);
        UUID assignee = UUID.randomUUID();
        startInstance(definitionId, UUID.randomUUID(), List.of(assignee));

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(assignee));
        mockMvc.perform(get("/api/v1/workflows/tasks/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(get("/api/v1/workflows/tasks/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
