package com.growdigitalbridge.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.expense.api.dto.Decision;
import com.growdigitalbridge.expense.api.dto.ExpenseClaimDtos;
import com.growdigitalbridge.expense.client.EmployeeClient;
import com.growdigitalbridge.expense.client.OrganizationClient;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
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
 * services, not something this test should stand up). A RabbitMQContainer is required because
 * this service's Spring context includes a real {@code @RabbitListener} bean (WorkflowEventListener).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ExpenseIntegrationTest {

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

    private ExpenseClaimDtos.CreateRequest sampleClaim(String category, String amount) {
        return new ExpenseClaimDtos.CreateRequest("USD",
                List.of(new ExpenseClaimDtos.LineItem(LocalDate.of(2026, 1, 10), category, new BigDecimal(amount), "trip expense")),
                List.of(new ExpenseClaimDtos.ReceiptRef(UUID.randomUUID())));
    }

    private ExpenseClaimDtos.Response createClaim(UUID self, String category, String amount) throws Exception {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        MvcResult result = mockMvc.perform(post("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleClaim(category, amount))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseClaimDtos.Response.class);
    }

    @Test
    void creatingAClaimComputesTotalFromLinesAndStartsAsDraft() throws Exception {
        UUID self = UUID.randomUUID();
        ExpenseClaimDtos.Response created = createClaim(self, "Travel", "125.50");

        assertThat(created.status().name()).isEqualTo("DRAFT");
        assertThat(created.employeeRef()).isEqualTo(self);
        assertThat(created.total()).isEqualByComparingTo("125.50");
        assertThat(created.lines()).hasSize(1);
        assertThat(created.receipts()).hasSize(1);
    }

    @Test
    void onlyTheOwnerMayUpdateOrSubmitTheirDraft() throws Exception {
        UUID owner = UUID.randomUUID();
        ExpenseClaimDtos.Response claim = createClaim(owner, "Meals", "40.00");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(patch("/api/v1/expenses/claims/" + claim.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.update.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.UpdateRequest("EUR", null, null, null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void submittingMovesDraftToSubmittedAndBlocksFurtherEdits() throws Exception {
        UUID self = UUID.randomUUID();
        ExpenseClaimDtos.Response claim = createClaim(self, "Lodging", "300.00");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Already submitted - cannot edit or resubmit a non-draft claim.
        mockMvc.perform(patch("/api/v1/expenses/claims/" + claim.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.update.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.UpdateRequest("EUR", null, null, null))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void selfCancellationIsAcceptedOnlyThroughThePatchEndpointWhileDraft() throws Exception {
        UUID self = UUID.randomUUID();
        ExpenseClaimDtos.Response claim = createClaim(self, "Supplies", "15.00");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        // Any status other than CANCELLED is rejected as a self-directed transition.
        mockMvc.perform(patch("/api/v1/expenses/claims/" + claim.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.update.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.UpdateRequest(null, null, null,
                                com.growdigitalbridge.expense.domain.ExpenseClaimStatus.APPROVED))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/expenses/claims/" + claim.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.update.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.UpdateRequest(null, null, null,
                                com.growdigitalbridge.expense.domain.ExpenseClaimStatus.CANCELLED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void decisionRequiresTeamOrAllApprovalScopeAndValidatesState() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        ExpenseClaimDtos.Response claim = createClaim(requester, "Client dinner", "88.20");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(requester));
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isOk());

        // Manager not in scope - treated as not found, matching Leave's identical pattern.
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isNotFound());

        // Manager in scope - decision succeeds.
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(requester));
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.team")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Already decided - cannot decide again.
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectionEndsTheClaimWithoutReachingReimbursableState() throws Exception {
        UUID requester = UUID.randomUUID();
        ExpenseClaimDtos.Response claim = createClaim(requester, "Parking", "12.00");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(requester));
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.DecisionRequest(Decision.REJECTED, "not eligible"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/reimbursements")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.reimburse"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reimbursementRequiresApprovedStateAndTheReimburseAuthority() throws Exception {
        UUID requester = UUID.randomUUID();
        ExpenseClaimDtos.Response claim = createClaim(requester, "Software license", "60.00");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(requester));
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isOk());

        // Not yet approved.
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/reimbursements")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.reimburse"))))
                .andExpect(status().isUnprocessableEntity());

        // Lacking expense.reimburse is rejected by the security filter chain itself.
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/reimbursements")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.all"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/reimbursements")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.reimburse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REIMBURSED"));
    }

    @Test
    void listingIsScopedBySelfTeamAndAllAuthorities() throws Exception {
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        createClaim(employeeA, "Travel", "10.00");
        createClaim(employeeB, "Travel", "20.00");

        // Reporting V1 authorization review, Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md):
        // each tier's real, legitimate 200 response must carry the matching page.scope - this is
        // the exact contract the Expense Summary report's authorization gate depends on, proving
        // a self-only (or team-only) caller's response is never tagged "ALL".
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeA));
        mockMvc.perform(get("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].employeeRef", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(employeeA.toString()))))
                .andExpect(jsonPath("$.page.scope").value("SELF"));

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(employeeB));
        mockMvc.perform(get("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.team"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].employeeRef", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(employeeB.toString()))))
                .andExpect(jsonPath("$.page.scope").value("TEAM"));

        mockMvc.perform(get("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    /**
     * Confirmed PostgreSQL parameter-typing defect fix (docs/REPORTING_AUTHORIZATION_REVIEW.md
     * Part A verification): {@code ExpenseClaimRepository.searchAll}'s {@code :from}/{@code :to}
     * parameters, each referenced in a standalone {@code is null} check with no other type
     * context, left Postgres's extended query protocol unable to determine either parameter's
     * type, producing "could not determine data type of parameter" for Expense Summary's own
     * call pattern (date range set, {@code status} omitted) - confirmed independent of whether
     * {@code status} was also set. Fixed by casting every occurrence of {@code :from}/{@code :to}
     * to an explicit date type. These tests reproduce the exact previously-crashing call pattern
     * and the surrounding ones the fix must not regress, including the empty-result case a
     * content-based heuristic could never have covered safely (see the doc's rejection of that
     * approach).
     */
    @Test
    void listingWorksWithFromAndToAndNoStatus() throws Exception {
        UUID employeeA = UUID.randomUUID();
        createClaim(employeeA, "Travel", "10.00");
        String today = java.time.LocalDate.now().toString();

        // This is Expense Summary's exact default call pattern - from/to set, status omitted -
        // which reproduced the confirmed defect directly before the fix.
        mockMvc.perform(get("/api/v1/expenses/claims?from=2000-01-01&to=" + today)
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    @Test
    void listingWorksWithFromAndToAndNoStatusWhenNoClaimsMatch() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/claims?from=1900-01-01&to=1900-01-02")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    @Test
    void listingWorksWithoutFromOrTo() throws Exception {
        UUID employeeA = UUID.randomUUID();
        createClaim(employeeA, "Travel", "10.00");

        mockMvc.perform(get("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    @Test
    void listingWorksWithStatusAndNoFromOrTo() throws Exception {
        UUID employeeA = UUID.randomUUID();
        createClaim(employeeA, "Travel", "10.00");

        mockMvc.perform(get("/api/v1/expenses/claims?status=DRAFT")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    @Test
    void listingWorksWithStatusAndFromAndToAllSetTogether() throws Exception {
        String today = java.time.LocalDate.now().toString();
        mockMvc.perform(get("/api/v1/expenses/claims?status=DRAFT&from=2000-01-01&to=" + today)
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.scope").value("ALL"));
    }

    @Test
    void createFailsClosedWhenTheCallersEmployeeIdentityCannotBeResolved() throws Exception {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleClaim("Travel", "10.00"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/claims")).andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientPermissionIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.create.self"))))
                .andExpect(status().isForbidden());
    }
}
