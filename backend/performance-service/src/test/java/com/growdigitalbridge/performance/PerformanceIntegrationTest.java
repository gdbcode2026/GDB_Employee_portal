package com.growdigitalbridge.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.performance.api.dto.GoalDtos;
import com.growdigitalbridge.performance.api.dto.PerformanceReviewDtos;
import com.growdigitalbridge.performance.api.dto.ReviewCycleDtos;
import com.growdigitalbridge.performance.client.EmployeeClient;
import com.growdigitalbridge.performance.client.OrganizationClient;
import com.growdigitalbridge.performance.domain.GoalStatus;
import java.time.LocalDate;
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
class PerformanceIntegrationTest {

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

    private ReviewCycleDtos.Response createCycle(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/performance/cycles")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCycleDtos.CreateRequest(
                                name, LocalDate.now().minusDays(10), LocalDate.now().plusDays(10)))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ReviewCycleDtos.Response.class);
    }

    @Test
    void goalLifecycleIsSelfScopedOnly() throws Exception {
        UUID owner = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));

        MvcResult created = mockMvc.perform(post("/api/v1/performance/goals")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.goal.manage.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalDtos.CreateRequest("Learn Kubernetes", null, "Pass CKA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        GoalDtos.Response goal = objectMapper.readValue(created.getResponse().getContentAsString(), GoalDtos.Response.class);

        mockMvc.perform(get("/api/v1/performance/goals")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(patch("/api/v1/performance/goals/" + goal.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.goal.manage.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalDtos.UpdateRequest(null, null, null, GoalStatus.IN_PROGRESS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // A different employee cannot update someone else's goal.
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(patch("/api/v1/performance/goals/" + goal.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.goal.manage.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalDtos.UpdateRequest(null, null, null, GoalStatus.COMPLETED))))
                .andExpect(status().isNotFound());
    }

    @Test
    void selfReviewIsCreatedAndSubmittedUnderTheSoleDocumentedSubmitPermission() throws Exception {
        UUID employee = UUID.randomUUID();
        ReviewCycleDtos.Response cycle = createCycle("Self Review Cycle");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employee));

        MvcResult created = mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.CreateRequest(cycle.id(), employee, employee))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        PerformanceReviewDtos.Response review = objectMapper.readValue(created.getResponse().getContentAsString(), PerformanceReviewDtos.Response.class);

        mockMvc.perform(post("/api/v1/performance/reviews/" + review.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.SubmitRequest("Strong quarter", "Great initiative"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rating").value("Strong quarter"));

        // Cannot submit an already-submitted review.
        mockMvc.perform(post("/api/v1/performance/reviews/" + review.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.SubmitRequest("x", "y"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reviewingSomeoneOutsideCallersTeamScopeIsRejected() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        ReviewCycleDtos.Response cycle = createCycle("Team Review Cycle");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());

        mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.CreateRequest(cycle.id(), stranger, manager))))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingAReviewAssigningSomeoneElseAsReviewerRequiresPerformanceManage() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID otherReviewer = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        ReviewCycleDtos.Response cycle = createCycle("Delegated Review Cycle");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));

        mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.CreateRequest(cycle.id(), report, otherReviewer))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.CreateRequest(cycle.id(), report, otherReviewer))))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateReviewForSameCycleEmployeeAndReviewerConflicts() throws Exception {
        UUID employee = UUID.randomUUID();
        ReviewCycleDtos.Response cycle = createCycle("Duplicate Check Cycle");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employee));

        var request = new PerformanceReviewDtos.CreateRequest(cycle.id(), employee, employee);
        mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void reviewCreationRequiresAnActiveCycle() throws Exception {
        UUID employee = UUID.randomUUID();
        ReviewCycleDtos.Response cycle = createCycle("Closed Cycle");
        mockMvc.perform(patch("/api/v1/performance/cycles/" + cycle.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCycleDtos.UpdateRequest(
                                null, null, null, com.growdigitalbridge.performance.domain.ReviewCycleStatus.CLOSED))))
                .andExpect(status().isOk());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employee));
        mockMvc.perform(post("/api/v1/performance/reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("performance.review.submit.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PerformanceReviewDtos.CreateRequest(cycle.id(), employee, employee))))
                .andExpect(status().isUnprocessableEntity());
    }
}
