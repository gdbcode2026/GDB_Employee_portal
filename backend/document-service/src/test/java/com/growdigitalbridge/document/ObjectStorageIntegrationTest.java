package com.growdigitalbridge.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.document.api.dto.DocumentDtos;
import com.growdigitalbridge.document.client.EmployeeClient;
import com.growdigitalbridge.document.client.OrganizationClient;
import com.growdigitalbridge.document.storage.ObjectStorageClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real object-storage flow end to end against a real S3-API-compatible server
 * ({@code adobe/s3mock} - see {@link TestObjectStorage}): actual bytes uploaded through {@code PUT
 * .../content}, actually stored, actually checksummed on {@code complete}, and actually
 * retrievable as a pre-signed download URL - not just metadata agreeing with itself.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ObjectStorageIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Container
    static GenericContainer<?> S3_MOCK = TestObjectStorage.container();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        TestObjectStorage.registerProperties(registry, S3_MOCK);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ObjectStorageClient objectStorageClient;

    @MockitoBean private EmployeeClient employeeClient;
    @MockitoBean private OrganizationClient organizationClient;

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private DocumentDtos.Response selfUpload(UUID owner, byte[] bytes) throws Exception {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(owner));
        MvcResult result = mockMvc.perform(post("/api/v1/documents/uploads")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentDtos.UploadRequest("HR", "application/pdf", (long) bytes.length, sha256Hex(bytes)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), DocumentDtos.Response.class);
    }

    private String objectKeyOf(DocumentDtos.Response document) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/documents/" + document.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self"), new SimpleGrantedAuthority("document.read.self"))))
                .andReturn();
        DocumentDtos.Response refreshed = objectMapper.readValue(result.getResponse().getContentAsString(), DocumentDtos.Response.class);
        return refreshed.latestVersion().objectKey();
    }

    @Test
    void uploadingContentActuallyStoresTheBytesAndCompleteAcceptsAMatchingRealChecksum() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] pdfBytes = "%PDF-1.4 fake payslip content for storage test".getBytes(StandardCharsets.UTF_8);
        DocumentDtos.Response document = selfUpload(owner, pdfBytes);
        String objectKey = objectKeyOf(document);

        assertThat(objectStorageClient.exists(objectKey)).isFalse();

        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(pdfBytes))
                .andExpect(status().isOk());

        assertThat(objectStorageClient.exists(objectKey)).isTrue();
        assertThat(objectStorageClient.getObject(objectKey)).isPresent();
        assertThat(objectStorageClient.getObject(objectKey).orElseThrow()).isEqualTo(pdfBytes);

        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest(sha256Hex(pdfBytes)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.latestVersion.scanStatus").value("CLEAN"));
    }

    @Test
    void tamperedBytesAreQuarantinedOnCompleteEvenWhenDeclaredChecksumsMatch() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] declaredBytes = "original-declared-bytes-for-checksum".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBytes = "corrupted!!-different-content-bytes!".getBytes(StandardCharsets.UTF_8);
        assertThat(tamperedBytes.length).isEqualTo(declaredBytes.length);
        DocumentDtos.Response document = selfUpload(owner, declaredBytes);

        // Upload different bytes of the same declared length - passes size validation, but the
        // real content no longer matches the checksum declared at upload time.
        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(tamperedBytes))
                .andExpect(status().isOk());

        // The complete request still declares the ORIGINAL checksum - metadata alone would say "match".
        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest(sha256Hex(declaredBytes)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUARANTINED"))
                .andExpect(jsonPath("$.latestVersion.scanStatus").value("QUARANTINED"));

        // Quarantined documents may never be downloaded.
        mockMvc.perform(get("/api/v1/documents/" + document.id() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void contentTypeMismatchOnUploadIsRejected() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] bytes = "some bytes".getBytes(StandardCharsets.UTF_8);
        DocumentDtos.Response document = selfUpload(owner, bytes);

        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.IMAGE_PNG)
                        .content(bytes))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sizeMismatchOnUploadIsRejected() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] declaredBytes = "twenty bytes exactly".getBytes(StandardCharsets.UTF_8);
        assertThat(declaredBytes.length).isEqualTo(20);
        DocumentDtos.Response document = selfUpload(owner, declaredBytes);

        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content("too short".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void availableDocumentDownloadReturnsARealShortLivedPresignedUrl() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] bytes = "presign-me-a-real-payslip-pdf".getBytes(StandardCharsets.UTF_8);
        DocumentDtos.Response document = selfUpload(owner, bytes);

        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(bytes))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest(sha256Hex(bytes)))))
                .andExpect(status().isOk());

        Instant beforeCall = Instant.now();
        MvcResult result = mockMvc.perform(get("/api/v1/documents/" + document.id() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isOk())
                .andReturn();
        DocumentDtos.DownloadResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), DocumentDtos.DownloadResponse.class);

        assertThat(response.downloadUrl()).isNotBlank();
        assertThat(response.downloadUrl()).doesNotContain(TestObjectStorage.SECRET_KEY);
        assertThat(response.downloadUrlExpiresAt()).isAfter(beforeCall);
        // Never a permanent/public link: it always carries the S3 pre-signed query parameters.
        assertThat(response.downloadUrl()).contains("X-Amz-Signature");
    }

    @Test
    void aStrangerCannotDownloadAnotherEmployeesDocument() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] bytes = "owners-private-payslip".getBytes(StandardCharsets.UTF_8);
        DocumentDtos.Response document = selfUpload(owner, bytes);
        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(bytes))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest(sha256Hex(bytes)))))
                .andExpect(status().isOk());

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));
        mockMvc.perform(get("/api/v1/documents/" + document.id() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void workloadCreatedPayslipCanBeDownloadedByItsOwnerWithARealPdf() throws Exception {
        UUID employeeOwner = UUID.randomUUID();
        byte[] payslipPdf = "%PDF-1.4 real payslip bytes from payroll-service".getBytes(StandardCharsets.UTF_8);

        MvcResult created = mockMvc.perform(post("/api/v1/documents/workload-uploads")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workload.document.upload")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.WorkloadUploadRequest(
                                employeeOwner, "PAYSLIP", "application/pdf", (long) payslipPdf.length, sha256Hex(payslipPdf)))))
                .andExpect(status().isCreated())
                .andReturn();
        DocumentDtos.Response document = objectMapper.readValue(created.getResponse().getContentAsString(), DocumentDtos.Response.class);

        mockMvc.perform(put("/api/v1/documents/uploads/" + document.id() + "/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workload.document.upload")))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(payslipPdf))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workload.document.upload")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest(sha256Hex(payslipPdf)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeOwner));
        MvcResult downloaded = mockMvc.perform(get("/api/v1/documents/" + document.id() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.read.self"))))
                .andExpect(status().isOk())
                .andReturn();
        DocumentDtos.DownloadResponse response = objectMapper.readValue(downloaded.getResponse().getContentAsString(), DocumentDtos.DownloadResponse.class);
        assertThat(response.downloadUrl()).isNotBlank();
    }

    @Test
    void fetchingAnObjectThatDoesNotExistInStorageIsHandledSafely() {
        Optional<byte[]> missing = objectStorageClient.getObject("no-such-object-key-" + UUID.randomUUID());
        assertThat(missing).isEmpty();
    }
}
