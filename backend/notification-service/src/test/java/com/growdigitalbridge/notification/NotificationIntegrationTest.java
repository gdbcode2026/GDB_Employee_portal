package com.growdigitalbridge.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.notification.api.dto.NotificationDtos;
import com.growdigitalbridge.notification.client.EmployeeClient;
import com.growdigitalbridge.notification.domain.Notification;
import com.growdigitalbridge.notification.domain.NotificationType;
import com.growdigitalbridge.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration and JPA mappings against genuine PostgreSQL, and the real
 * security filter chain, while mocking only Employee Service (a separate service) - the same
 * shape every other service's REST-focused integration test already uses.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoBean
    private EmployeeClient employeeClient;

    private Notification create(UUID recipient, NotificationType type, String title, String message, Instant createdAt) {
        return notificationRepository.save(new Notification(UUID.randomUUID(), recipient, type, title, message, UUID.randomUUID(), createdAt));
    }

    private void selfIs(UUID employeeRef) {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeRef));
    }

    @Test
    void employeeSeesOnlyTheirOwnNotificationsAndTheirOwnUnreadCount() throws Exception {
        UUID self = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        create(self, NotificationType.LEAVE_APPROVED, "Leave approved", "Your leave was approved.", Instant.now());
        create(self, NotificationType.EXPENSE_SUBMITTED, "Expense submitted", "Your claim was submitted.", Instant.now());
        create(other, NotificationType.LEAVE_APPROVED, "Leave approved", "Someone else's notification.", Instant.now());
        selfIs(self);

        MvcResult result = mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andReturn();
        NotificationDtos.ListResponse body = objectMapper.readValue(result.getResponse().getContentAsString(), NotificationDtos.ListResponse.class);
        assertThat(body.items()).hasSize(2);
        assertThat(body.page().total()).isEqualTo(2);
    }

    @Test
    void employeeCanRetrieveTheirOwnNotificationById() throws Exception {
        UUID self = UUID.randomUUID();
        Notification notification = create(self, NotificationType.ATTENDANCE_REGULARIZATION_APPROVED,
                "Attendance regularization approved", "Approved.", Instant.now());
        selfIs(self);

        mockMvc.perform(get("/api/v1/notifications/" + notification.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.read").value(false));
    }

    @Test
    void anotherEmployeeCannotAccessTheNotificationByIdOrMarkItRead() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        Notification notification = create(owner, NotificationType.LEAVE_REJECTED, "Leave rejected", "Rejected.", Instant.now());
        selfIs(intruder);

        mockMvc.perform(get("/api/v1/notifications/" + notification.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/notifications/" + notification.getId() + "/read")
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isNotFound());

        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void gettingANonexistentNotificationReturnsNotFound() throws Exception {
        selfIs(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/notifications/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void markingANotificationReadSetsReadStateAndReadAtAndIsIdempotent() throws Exception {
        UUID self = UUID.randomUUID();
        Notification notification = create(self, NotificationType.EXPENSE_APPROVED, "Expense approved", "Approved.", Instant.now());
        selfIs(self);

        MvcResult first = mockMvc.perform(post("/api/v1/notifications/" + notification.getId() + "/read")
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andReturn();
        NotificationDtos.Response firstBody = objectMapper.readValue(first.getResponse().getContentAsString(), NotificationDtos.Response.class);
        assertThat(firstBody.readAt()).isNotNull();

        MvcResult second = mockMvc.perform(post("/api/v1/notifications/" + notification.getId() + "/read")
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isOk())
                .andReturn();
        NotificationDtos.Response secondBody = objectMapper.readValue(second.getResponse().getContentAsString(), NotificationDtos.Response.class);
        // Compared at millisecond precision: the first response reflects the in-memory Instant set
        // by this call; the second reflects that same value after a Postgres TIMESTAMPTZ(6)
        // round-trip, which can round rather than floor at the microsecond boundary. Millisecond
        // granularity safely clears that rounding artifact while still proving the value was not
        // advanced to a materially later time on this second (already-read) call.
        assertThat(secondBody.readAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(firstBody.readAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void markAllReadMarksOnlyTheCallersOwnUnreadNotifications() throws Exception {
        UUID self = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Notification selfOne = create(self, NotificationType.LEAVE_APPROVED, "A", "A", Instant.now());
        Notification selfTwo = create(self, NotificationType.LEAVE_REQUESTED, "B", "B", Instant.now());
        Notification othersOne = create(other, NotificationType.LEAVE_APPROVED, "C", "C", Instant.now());
        selfIs(self);

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findById(selfOne.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(selfTwo.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(othersOne.getId()).orElseThrow().isRead()).isFalse();

        MvcResult afterward = mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("notification.read.self"))))
                .andExpect(status().isOk()).andReturn();
        NotificationDtos.ListResponse body = objectMapper.readValue(afterward.getResponse().getContentAsString(), NotificationDtos.ListResponse.class);
        assertThat(body.unreadCount()).isZero();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingPermissionIsRejectedAsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").with(jwt())).andExpect(status().isForbidden());
    }
}
