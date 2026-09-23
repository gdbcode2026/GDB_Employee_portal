package com.growdigitalbridge.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.asset.api.dto.AssetAssignmentDtos;
import com.growdigitalbridge.asset.api.dto.AssetDtos;
import com.growdigitalbridge.asset.api.dto.AssetRequestDtos;
import com.growdigitalbridge.asset.client.EmployeeClient;
import java.util.Optional;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration and JPA mappings against genuine PostgreSQL, and the
 * real security filter chain, while mocking only Employee Service (a separate service, not
 * something this test should stand up).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AssetIntegrationTest {

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

    private AssetDtos.Response createAsset(String tag) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetDtos.CreateRequest(tag, "Laptop", "SN-" + tag))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AssetDtos.Response.class);
    }

    @Test
    void creatingAnAssetRequiresAssetManage() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.read.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetDtos.CreateRequest("A-1", "Laptop", null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetDtos.CreateRequest("A-1", "Laptop", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void duplicateAssetTagConflicts() throws Exception {
        createAsset("DUP-1");

        mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetDtos.CreateRequest("DUP-1", "Monitor", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void listingAllAssetsRequiresAssetReadAll() throws Exception {
        createAsset("LIST-1");

        mockMvc.perform(get("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.read.self"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void assigningAnAssetMovesItToAssignedAndOnlyOneOpenAssignmentAtATime() throws Exception {
        AssetDtos.Response asset = createAsset("ASSIGN-1");
        UUID employeeRef = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/assets/" + asset.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(employeeRef))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeRef").value(employeeRef.toString()));

        // Already assigned - a second assignment attempt is rejected.
        mockMvc.perform(post("/api/v1/assets/" + asset.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(UUID.randomUUID()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returningAnAssignmentFreesTheAssetForReassignment() throws Exception {
        AssetDtos.Response asset = createAsset("RETURN-1");
        UUID employeeRef = UUID.randomUUID();

        MvcResult assignResult = mockMvc.perform(post("/api/v1/assets/" + asset.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(employeeRef))))
                .andExpect(status().isCreated()).andReturn();
        AssetAssignmentDtos.Response assignment = objectMapper.readValue(
                assignResult.getResponse().getContentAsString(), AssetAssignmentDtos.Response.class);

        mockMvc.perform(post("/api/v1/assignments/" + assignment.id() + "/return")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.ReturnRequest("Good condition"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditionNotes").value("Good condition"));

        // Returned - can now be assigned to someone else.
        mockMvc.perform(post("/api/v1/assets/" + asset.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(UUID.randomUUID()))))
                .andExpect(status().isCreated());

        // Already returned - cannot return again.
        mockMvc.perform(post("/api/v1/assignments/" + assignment.id() + "/return")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.ReturnRequest(null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void assetsMeReturnsOnlyTheCallersOwnCurrentlyAssignedAssets() throws Exception {
        AssetDtos.Response mine = createAsset("MINE-1");
        AssetDtos.Response someoneElses = createAsset("THEIRS-1");
        UUID self = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/assets/" + mine.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(self))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/assets/" + someoneElses.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(UUID.randomUUID()))))
                .andExpect(status().isCreated());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));
        mockMvc.perform(get("/api/v1/assets/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].tag").value("MINE-1"));
    }

    @Test
    void submittingAnAssetRequestPersistsItAsSubmittedWithNoWorkflowCallMade() throws Exception {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));

        MvcResult result = mockMvc.perform(post("/api/v1/asset-requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.request.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetRequestDtos.CreateRequest("Laptop", "Need for new hire"))))
                .andExpect(status().isCreated()).andReturn();
        AssetRequestDtos.Response created = objectMapper.readValue(result.getResponse().getContentAsString(), AssetRequestDtos.Response.class);

        assertThat(created.status().name()).isEqualTo("SUBMITTED");
        assertThat(created.employeeRef()).isEqualTo(self);
        assertThat(created.workflowRef()).isNull();
    }
}
