package com.growdigitalbridge.notification.messaging;

import com.growdigitalbridge.notification.domain.Notification;
import com.growdigitalbridge.notification.domain.NotificationType;
import com.growdigitalbridge.notification.repository.NotificationRepository;
import com.growdigitalbridge.notification.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link DomainEventListener} against a real broker and a real database: each of the six
 * bound event types produces exactly one correctly-typed {@link Notification} for the employee
 * its own payload names, and a duplicate delivery of the same event ID never creates a second
 * row - the same idempotency proof shape as {@code AssetMessagingIntegrationTest}'s {@code
 * employeeDeactivatedEventIsConsumedIdempotently}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationMessagingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    private DomainEvent event(UUID eventId, String eventType, String producer, UUID aggregateId, Map<String, Object> payload) {
        return new DomainEvent(eventId, eventType, 1, Instant.now(), UUID.randomUUID(), producer, aggregateId, payload);
    }

    private void publish(DomainEvent event) {
        rabbitTemplate.convertAndSend("gdb.domain.events", event.eventType(), event);
    }

    @Test
    void leaveApprovedEventCreatesANotificationForTheEmployee() throws InterruptedException {
        UUID employeeRef = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        publish(event(UUID.randomUUID(), "leave.approved.v1", "leave-service", requestId, Map.of(
                "requestId", requestId.toString(), "employeeId", employeeRef.toString(), "approvedUnits", "2")));

        List<Notification> notifications = awaitNotificationsFor(employeeRef, 1);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.LEAVE_APPROVED);
        assertThat(notifications.get(0).isRead()).isFalse();
    }

    @Test
    void duplicateDeliveryOfTheSameEventDoesNotCreateADuplicateNotification() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID employeeRef = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        DomainEvent event = event(eventId, "leave.rejected.v1", "leave-service", requestId, Map.of(
                "requestId", requestId.toString(), "employeeId", employeeRef.toString()));

        publish(event);
        publish(event);

        awaitProcessed(eventId);
        List<Notification> notifications = awaitNotificationsFor(employeeRef, 1);
        assertThat(notifications).hasSize(1);
    }

    @Test
    void everyMappedEventTypeProducesTheCorrectlyTypedNotification() throws InterruptedException {
        UUID leaveRequested = UUID.randomUUID();
        UUID expenseSubmitted = UUID.randomUUID();
        UUID expenseApproved = UUID.randomUUID();
        UUID attendanceApproved = UUID.randomUUID();

        publish(event(UUID.randomUUID(), "leave.requested.v1", "leave-service", UUID.randomUUID(), Map.of(
                "requestId", UUID.randomUUID().toString(), "employeeId", leaveRequested.toString(),
                "leaveTypeId", UUID.randomUUID().toString(), "startDate", "2026-01-05", "endDate", "2026-01-06")));
        publish(event(UUID.randomUUID(), "expense.submitted.v1", "expense-service", UUID.randomUUID(), Map.of(
                "claimId", UUID.randomUUID().toString(), "employeeId", expenseSubmitted.toString(),
                "currency", "INR", "total", "500.00")));
        publish(event(UUID.randomUUID(), "expense.approved.v1", "expense-service", UUID.randomUUID(), Map.of(
                "claimId", UUID.randomUUID().toString(), "employeeId", expenseApproved.toString(),
                "approvedTotal", "500.00", "currency", "INR")));
        publish(event(UUID.randomUUID(), "attendance.regularization-approved.v1", "attendance-service", UUID.randomUUID(), Map.of(
                "requestId", UUID.randomUUID().toString(), "attendanceId", UUID.randomUUID().toString(),
                "employeeId", attendanceApproved.toString())));

        assertThat(awaitNotificationsFor(leaveRequested, 1).get(0).getType()).isEqualTo(NotificationType.LEAVE_REQUESTED);
        assertThat(awaitNotificationsFor(expenseSubmitted, 1).get(0).getType()).isEqualTo(NotificationType.EXPENSE_SUBMITTED);
        assertThat(awaitNotificationsFor(expenseApproved, 1).get(0).getType()).isEqualTo(NotificationType.EXPENSE_APPROVED);
        assertThat(awaitNotificationsFor(attendanceApproved, 1).get(0).getType()).isEqualTo(NotificationType.ATTENDANCE_REGULARIZATION_APPROVED);
    }

    private List<Notification> awaitNotificationsFor(UUID recipient, int expectedCount) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            List<Notification> found = notificationRepository.findAll().stream()
                    .filter(n -> n.getRecipientEmployeeRef().equals(recipient)).toList();
            if (found.size() >= expectedCount) {
                return found;
            }
            Thread.sleep(300);
        }
        return notificationRepository.findAll().stream().filter(n -> n.getRecipientEmployeeRef().equals(recipient)).toList();
    }

    private void awaitProcessed(UUID eventId) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (processedEventRepository.existsById(eventId)) {
                return;
            }
            Thread.sleep(300);
        }
    }
}
