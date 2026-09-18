package com.growdigitalbridge.leave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.leave.api.dto.Decision;
import com.growdigitalbridge.leave.api.dto.LeaveBalanceDtos;
import com.growdigitalbridge.leave.api.dto.LeaveRequestDtos;
import com.growdigitalbridge.leave.api.dto.LeaveTypeDtos;
import com.growdigitalbridge.leave.client.EmployeeClient;
import com.growdigitalbridge.leave.client.OrganizationClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration (including seeded leave types) and JPA mappings against
 * genuine PostgreSQL, and the real security filter chain, while mocking only Employee/
 * Organization Service (separate services, not something this test should stand up). Balance
 * amounts are asserted by deserializing the full response and comparing BigDecimal values
 * (isEqualByComparingTo), rather than jsonPath numeric literals, since JSON-number parsing
 * libraries do not reliably round-trip BigDecimal scale/type for direct literal equality.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LeaveIntegrationTest {

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

    private UUID annualLeaveTypeId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/leave/types")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.read.self"))))
                .andExpect(status().isOk()).andReturn();
        List<LeaveTypeDtos.Response> types = objectMapper.readValue(result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, LeaveTypeDtos.Response.class));
        return types.stream().filter(t -> t.code().equals("ANNUAL")).findFirst().orElseThrow().id();
    }

    private void allocate(UUID employeeId, UUID leaveTypeId, BigDecimal allocated) throws Exception {
        var request = new LeaveBalanceDtos.AllocateRequest(employeeId, leaveTypeId, Year.now().getValue(), allocated);
        mockMvc.perform(post("/api/v1/leave/balances")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private LeaveBalanceDtos.Response fetchSelfBalance() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/leave/balances/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.read.self"))))
                .andExpect(status().isOk()).andReturn();
        List<LeaveBalanceDtos.Response> balances = objectMapper.readValue(result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, LeaveBalanceDtos.Response.class));
        return balances.get(0);
    }

    @Test
    void applyingLeaveReservesBalanceAndApprovalConsumesIt() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = annualLeaveTypeId();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));
        allocate(employeeId, leaveTypeId, new BigDecimal("10"));

        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(2);
        var createRequest = new LeaveRequestDtos.CreateRequest(leaveTypeId, start, end, "Family trip");
        MvcResult created = mockMvc.perform(post("/api/v1/leave/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        LeaveRequestDtos.Response leaveRequest = objectMapper.readValue(created.getResponse().getContentAsString(), LeaveRequestDtos.Response.class);
        assertThat(leaveRequest.units()).isEqualByComparingTo("3");

        LeaveBalanceDtos.Response afterSubmit = fetchSelfBalance();
        assertThat(afterSubmit.reserved()).isEqualByComparingTo("3");
        assertThat(afterSubmit.available()).isEqualByComparingTo("7");

        mockMvc.perform(post("/api/v1/leave/requests/" + leaveRequest.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.DecisionRequest(Decision.APPROVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        LeaveBalanceDtos.Response afterApproval = fetchSelfBalance();
        assertThat(afterApproval.reserved()).isEqualByComparingTo("0");
        assertThat(afterApproval.used()).isEqualByComparingTo("3");
        assertThat(afterApproval.available()).isEqualByComparingTo("7");
    }

    @Test
    void insufficientBalanceIsRejectedWithUnprocessableEntity() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = annualLeaveTypeId();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));
        allocate(employeeId, leaveTypeId, new BigDecimal("1"));

        LocalDate start = LocalDate.now().plusDays(20);
        var createRequest = new LeaveRequestDtos.CreateRequest(leaveTypeId, start, start.plusDays(4), "Too long");

        mockMvc.perform(post("/api/v1/leave/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectionAndCancellationBothReleaseTheReservedBalance() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = annualLeaveTypeId();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));
        allocate(employeeId, leaveTypeId, new BigDecimal("10"));

        // Rejection releases the reservation.
        LocalDate start1 = LocalDate.now().plusDays(30);
        MvcResult created1 = mockMvc.perform(post("/api/v1/leave/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.CreateRequest(leaveTypeId, start1, start1.plusDays(1), null))))
                .andExpect(status().isCreated()).andReturn();
        LeaveRequestDtos.Response request1 = objectMapper.readValue(created1.getResponse().getContentAsString(), LeaveRequestDtos.Response.class);

        mockMvc.perform(post("/api/v1/leave/requests/" + request1.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.DecisionRequest(Decision.REJECTED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        LeaveBalanceDtos.Response afterRejection = fetchSelfBalance();
        assertThat(afterRejection.reserved()).isEqualByComparingTo("0");
        assertThat(afterRejection.used()).isEqualByComparingTo("0");

        // Cancellation also releases the reservation.
        LocalDate start2 = LocalDate.now().plusDays(40);
        MvcResult created2 = mockMvc.perform(post("/api/v1/leave/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.CreateRequest(leaveTypeId, start2, start2, null))))
                .andExpect(status().isCreated()).andReturn();
        LeaveRequestDtos.Response request2 = objectMapper.readValue(created2.getResponse().getContentAsString(), LeaveRequestDtos.Response.class);

        mockMvc.perform(post("/api/v1/leave/requests/" + request2.id() + "/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.cancel.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(fetchSelfBalance().reserved()).isEqualByComparingTo("0");

        // A cancelled request cannot be cancelled again.
        mockMvc.perform(post("/api/v1/leave/requests/" + request2.id() + "/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.cancel.self"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void teamScopedDecisionIsAuthorizedOnlyThroughOrganizationServiceResolution() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID leaveTypeId = annualLeaveTypeId();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));
        allocate(employeeId, leaveTypeId, new BigDecimal("10"));

        LocalDate start = LocalDate.now().plusDays(50);
        MvcResult created = mockMvc.perform(post("/api/v1/leave/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.CreateRequest(leaveTypeId, start, start, null))))
                .andExpect(status().isCreated()).andReturn();
        LeaveRequestDtos.Response request = objectMapper.readValue(created.getResponse().getContentAsString(), LeaveRequestDtos.Response.class);

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(managerId));
        when(organizationClient.resolveTeamScope(managerId)).thenReturn(Set.of());

        mockMvc.perform(post("/api/v1/leave/requests/" + request.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.approve.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.DecisionRequest(Decision.APPROVED))))
                .andExpect(status().isNotFound());

        when(organizationClient.resolveTeamScope(managerId)).thenReturn(Set.of(employeeId));

        mockMvc.perform(post("/api/v1/leave/requests/" + request.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.approve.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.DecisionRequest(Decision.APPROVED))))
                .andExpect(status().isOk());
    }
}
