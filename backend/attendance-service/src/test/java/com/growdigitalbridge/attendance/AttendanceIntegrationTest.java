package com.growdigitalbridge.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.attendance.api.dto.AttendanceDtos;
import com.growdigitalbridge.attendance.api.dto.Decision;
import com.growdigitalbridge.attendance.api.dto.RegularizationDtos;
import com.growdigitalbridge.attendance.api.dto.WfhRequestDtos;
import com.growdigitalbridge.attendance.client.EmployeeClient;
import com.growdigitalbridge.attendance.client.OrganizationClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.testcontainers.containers.RabbitMQContainer;
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
 * services, not something this test should stand up). A real RabbitMQ container is required
 * too - not just Postgres - because the Spring context here includes a real
 * {@code @RabbitListener} bean ({@code LeaveEventListener}), which connects at context startup;
 * without an explicit broker, it would silently depend on whatever happens to be listening on
 * the host's default AMQP port, which is exactly the kind of environment-dependent flakiness
 * Testcontainers exists to avoid.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AttendanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeClient employeeClient;

    @MockitoBean
    private OrganizationClient organizationClient;

    @Test
    void checkInThenCheckOutLifecycleAndDuplicateGuardsWorkEndToEnd() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));

        mockMvc.perform(post("/api/v1/attendance/check-ins")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.create.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeRef").value(employeeId.toString()))
                .andExpect(jsonPath("$.checkOutAt").doesNotExist());

        mockMvc.perform(post("/api/v1/attendance/check-ins")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.create.self"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/attendance/check-outs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.create.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkOutAt").exists());

        mockMvc.perform(post("/api/v1/attendance/check-outs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.create.self"))))
                .andExpect(status().isConflict());
    }

    @Test
    void finalizeIsAuthorizedOnlyThroughOrganizationServiceTeamScope() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));

        MvcResult created = mockMvc.perform(post("/api/v1/attendance/check-ins")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.create.self"))))
                .andExpect(status().isOk()).andReturn();
        AttendanceDtos.Response record = objectMapper.readValue(created.getResponse().getContentAsString(), AttendanceDtos.Response.class);

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(managerId));
        when(organizationClient.resolveTeamScope(managerId)).thenReturn(Set.of());

        // Organization Service does not include this employee in the manager's scope, so
        // finalize must be denied (masked as not-found) even though the caller holds the
        // finalize authority - proving scope comes only from Organization Service.
        mockMvc.perform(post("/api/v1/attendance/" + record.id() + "/finalize")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.finalize.team"))))
                .andExpect(status().isNotFound());

        when(organizationClient.resolveTeamScope(managerId)).thenReturn(Set.of(employeeId));

        mockMvc.perform(post("/api/v1/attendance/" + record.id() + "/finalize")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.finalize.team"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        mockMvc.perform(post("/api/v1/attendance/" + record.id() + "/finalize")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.finalize.team"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void wfhRequestCreateAndDirectDecisionLifecycle() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));

        var createRequest = new WfhRequestDtos.CreateRequest(LocalDate.now(), LocalDate.now().plusDays(1), "Home network setup");
        MvcResult created = mockMvc.perform(post("/api/v1/attendance/wfh-requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("wfh.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated()).andReturn();
        WfhRequestDtos.Response wfh = objectMapper.readValue(created.getResponse().getContentAsString(), WfhRequestDtos.Response.class);

        mockMvc.perform(post("/api/v1/attendance/wfh-requests/" + wfh.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("wfh.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WfhRequestDtos.DecisionRequest(Decision.APPROVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/attendance/wfh-requests/" + wfh.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("wfh.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WfhRequestDtos.DecisionRequest(Decision.APPROVED))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void regularizationApprovalCorrectsTheUnderlyingAttendanceRecordEvenWhenNoneExisted() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));
        LocalDate workDate = LocalDate.now().minusDays(3);
        Instant correctedCheckIn = workDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(9 * 3600);
        Instant correctedCheckOut = workDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(18 * 3600);

        var createRequest = new RegularizationDtos.CreateRequest(workDate, correctedCheckIn, correctedCheckOut, "Forgot to check in");
        MvcResult created = mockMvc.perform(post("/api/v1/attendance/regularizations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.regularize.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated()).andReturn();
        RegularizationDtos.Response regularization = objectMapper.readValue(created.getResponse().getContentAsString(), RegularizationDtos.Response.class);

        mockMvc.perform(post("/api/v1/attendance/regularizations/" + regularization.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.regularize.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegularizationDtos.DecisionRequest(Decision.APPROVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/attendance/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].checkInAt").exists())
                .andExpect(jsonPath("$.items[0].checkOutAt").exists());
    }
}
