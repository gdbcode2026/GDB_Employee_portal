package com.growdigitalbridge.notification.messaging;

import com.growdigitalbridge.notification.domain.Notification;
import com.growdigitalbridge.notification.domain.NotificationType;
import com.growdigitalbridge.notification.domain.ProcessedEvent;
import com.growdigitalbridge.notification.repository.NotificationRepository;
import com.growdigitalbridge.notification.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a subset of existing domain events into one in-app {@link Notification} each, for the
 * employee the event's own payload unambiguously names. Idempotent via the platform's standard
 * inbox/{@link ProcessedEvent} pattern - exactly mirroring {@code LeaveEventListener} and
 * {@code OrganizationMessagingTopology}'s {@code EmployeeEventListener} in every other service.
 *
 * <p>Six event types are bound (see {@link NotificationMessagingTopology}), each inspected
 * against its actual publisher before being added here - not invented:
 * <ul>
 *   <li>{@code leave.requested.v1} / {@code leave.approved.v1} / {@code leave.rejected.v1}
 *   ({@code LeaveRequestService}) - payload carries {@code employeeId} (the requester).</li>
 *   <li>{@code expense.submitted.v1} / {@code expense.approved.v1}
 *   ({@code ExpenseClaimService}) - payload carries {@code employeeId} (the claimant).</li>
 *   <li>{@code attendance.regularization-approved.v1} ({@code RegularizationService}) - payload
 *   carries {@code employeeId}.</li>
 * </ul>
 *
 * <p><b>Deliberately NOT consumed</b>, despite docs/architecture/COMMUNICATION.md listing
 * Notification as an eventual consumer of each: {@code workflow.completed.v1}'s documented
 * minimum payload is "workflow ID, subject type/ID, outcome, decision time" - confirmed by
 * reading {@code WorkflowInstanceService}/{@code ApprovalTaskService} - it carries no employee
 * reference at all, only {@code subjectRef} (a foreign reference to the owning domain's own
 * request record, e.g. a leave request or expense claim ID, not an employee ID). Resolving a
 * recipient from it would require this service to also know every other domain's subject-type
 * shape and look up its owning employee - a new integration this V1 increment does not add.
 * Likewise no document/policy-publication event is consumed: Document Service publishes only
 * the generic {@code document.uploaded.v1} (any document, including internal/system uploads),
 * not a distinct policy-publication event, so there is no event here whose business meaning is
 * specifically "a policy was published." Both are flagged limitations, not oversights - see
 * the final implementation report.
 */
@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public DomainEventListener(ProcessedEventRepository processedEventRepository, NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @RabbitListener(queues = "notification.domain-events.v1")
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }

        Notification notification = toNotification(event);
        if (notification != null) {
            notificationRepository.save(notification);
        } else {
            log.warn("No notification mapping for event type {} (event {})", event.eventType(), event.eventId());
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }

    private Notification toNotification(DomainEvent event) {
        Map<String, Object> payload = event.payload();
        Instant now = Instant.now();
        return switch (event.eventType()) {
            case "leave.requested.v1" -> new Notification(UUID.randomUUID(), employeeId(payload), NotificationType.LEAVE_REQUESTED,
                    "Leave request submitted",
                    "Your leave request for " + payload.get("startDate") + " to " + payload.get("endDate") + " has been submitted.",
                    event.eventId(), now);
            case "leave.approved.v1" -> new Notification(UUID.randomUUID(), employeeId(payload), NotificationType.LEAVE_APPROVED,
                    "Leave request approved",
                    "Your leave request has been approved (" + payload.get("approvedUnits") + " day(s)).",
                    event.eventId(), now);
            case "leave.rejected.v1" -> new Notification(UUID.randomUUID(), employeeId(payload), NotificationType.LEAVE_REJECTED,
                    "Leave request rejected",
                    "Your leave request has been rejected.",
                    event.eventId(), now);
            case "expense.submitted.v1" -> new Notification(UUID.randomUUID(), employeeId(payload), NotificationType.EXPENSE_SUBMITTED,
                    "Expense claim submitted",
                    "Your expense claim for " + payload.get("currency") + " " + payload.get("total") + " has been submitted.",
                    event.eventId(), now);
            case "expense.approved.v1" -> new Notification(UUID.randomUUID(), employeeId(payload), NotificationType.EXPENSE_APPROVED,
                    "Expense claim approved",
                    "Your expense claim has been approved for " + payload.get("currency") + " " + payload.get("approvedTotal") + ".",
                    event.eventId(), now);
            case "attendance.regularization-approved.v1" -> new Notification(UUID.randomUUID(), employeeId(payload),
                    NotificationType.ATTENDANCE_REGULARIZATION_APPROVED,
                    "Attendance regularization approved",
                    "Your attendance regularization request has been approved.",
                    event.eventId(), now);
            default -> null;
        };
    }

    private UUID employeeId(Map<String, Object> payload) {
        return UUID.fromString(String.valueOf(payload.get("employeeId")));
    }
}
