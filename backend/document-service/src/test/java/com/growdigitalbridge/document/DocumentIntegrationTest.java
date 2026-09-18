package com.growdigitalbridge.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.document.api.dto.DocumentDtos;
import com.growdigitalbridge.document.api.dto.PolicyDtos;
import com.growdigitalbridge.document.client.EmployeeClient;
import com.growdigitalbridge.document.client.OrganizationClient;
import com.growdigitalbridge.document.domain.PolicyStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration and JPA mappings against genuine PostgreSQL, and the
 * real security filter chain, while mocking only Employee/Organization Service (separate
 * services, not something this test should stand up). A real RabbitMQ container is required
 * too - not just Postgres - because the Spring context here includes a real
 * {@code @RabbitListener} bean ({@code EmployeeEventListener}), which connects at context
 * startup; without an explicit broker, it would silently depend on whatever happens to be
 * listening on the host's default AMQP port.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DocumentIntegrationTest {

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

    private DocumentDtos.Response upload(UUID owner, String checksum) throws Exception {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        MvcResult result = mockMvc.perform(post("/api/v1/documents/uploads")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentDtos.UploadRequest("HR", "application/pdf", 1024L, checksum))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_SCAN"))
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), DocumentDtos.Response.class);
    }

    @Test
    void completingWithMatchingChecksumMarksTheDocumentAvailable() throws Exception {
        UUID owner = UUID.randomUUID();
        DocumentDtos.Response document = upload(owner, "sha256-abc");

        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-abc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.latestVersion.scanStatus").value("CLEAN"));

        // Already completed - cannot complete twice.
        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-abc"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void completingWithMismatchedChecksumQuarantinesTheDocument() throws Exception {
        UUID owner = UUID.randomUUID();
        DocumentDtos.Response document = upload(owner, "sha256-original");

        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-different"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUARANTINED"))
                .andExpect(jsonPath("$.latestVersion.scanStatus").value("QUARANTINED"));
    }

    @Test
    void downloadRequiresAvailableStatusAndDocumentedReadScope() throws Exception {
        UUID owner = UUID.randomUUID();
        DocumentDtos.Response document = upload(owner, "sha256-download");

        // Not yet completed - still PENDING_SCAN, not downloadable.
        mockMvc.perform(get("/api/v1/documents/" + document.id() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-download"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/documents/" + document.id() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checksum").value("sha256-download"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"));

        // A stranger without self/team/all visibility cannot see it at all.
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(get("/api/v1/documents/" + document.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void teamScopedVisibilityIsAuthorizedOnlyThroughOrganizationServiceResolution() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        DocumentDtos.Response document = upload(owner, "sha256-team");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(manager));
        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/documents/" + document.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.team"))))
                .andExpect(status().isNotFound());

        when(organizationClient.resolveTeamScope(manager)).thenReturn(Set.of(owner));

        mockMvc.perform(get("/api/v1/documents/" + document.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.team"))))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTheUploaderOrAManageOverrideMayCompleteAnUpload() throws Exception {
        UUID owner = UUID.randomUUID();
        DocumentDtos.Response document = upload(owner, "sha256-owner-only");

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-owner-only"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-owner-only"))))
                .andExpect(status().isOk());
    }

    @Test
    void policyLifecycleReferencesAnExistingDocument() throws Exception {
        UUID owner = UUID.randomUUID();
        DocumentDtos.Response document = upload(owner, "sha256-policy");

        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("policy.publish")))
                        .content(objectMapper.writeValueAsString(new PolicyDtos.CreateRequest(UUID.randomUUID(), "Ghost policy"))))
                .andExpect(status().isNotFound());

        MvcResult created = mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("policy.publish")))
                        .content(objectMapper.writeValueAsString(new PolicyDtos.CreateRequest(document.id(), "Remote Work Policy"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn();
        PolicyDtos.Response policy = objectMapper.readValue(created.getResponse().getContentAsString(), PolicyDtos.Response.class);

        mockMvc.perform(get("/api/v1/policies")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(patch("/api/v1/policies/" + policy.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("policy.publish")))
                        .content(objectMapper.writeValueAsString(new PolicyDtos.UpdateRequest("Remote Work Policy v2", PolicyStatus.DRAFT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Remote Work Policy v2"));
    }
}
