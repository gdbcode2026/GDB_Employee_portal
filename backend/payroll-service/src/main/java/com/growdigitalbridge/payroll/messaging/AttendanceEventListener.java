package com.growdigitalbridge.payroll.messaging;

import com.growdigitalbridge.payroll.domain.PayrollAttendanceInput;
import com.growdigitalbridge.payroll.domain.ProcessedEvent;
import com.growdigitalbridge.payroll.repository.PayrollAttendanceInputRepository;
import com.growdigitalbridge.payroll.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code attendance.finalized.v1} (Section I, decision 6) and persists the exact fields
 * the event carries ({@code attendanceId}, {@code employeeId}, {@code workDate}) as a {@link
 * PayrollAttendanceInput} - a snapshot for {@code ProrationPolicy} to read later, never a fetch
 * back to Attendance Service. Attendance Service only ever publishes this event type for an
 * already-finalized record, but this listener defensively re-checks the event's own {@code
 * status} field anyway (defense in depth against a future producer change) and ignores anything
 * that is not exactly {@code FINALIZED} - "ignore non-finalized attendance" (item 4) is enforced
 * here, not just assumed from the event name. The event is still marked processed either way, so
 * a non-finalized payload is never redelivered. Idempotent via the platform's standard inbox/
 * {@link ProcessedEvent} pattern: a duplicate delivery of the same event ID is a no-op.
 */
@Component
public class AttendanceEventListener {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final PayrollAttendanceInputRepository attendanceInputRepository;

    public AttendanceEventListener(ProcessedEventRepository processedEventRepository,
                                    PayrollAttendanceInputRepository attendanceInputRepository) {
        this.processedEventRepository = processedEventRepository;
        this.attendanceInputRepository = attendanceInputRepository;
    }

    @RabbitListener(queues = "payroll.attendance-finalized.v1")
    @Transactional
    public void onAttendanceFinalized(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }

        Instant now = Instant.now();
        String status = String.valueOf(event.payload().get("status"));
        if (!"FINALIZED".equals(status)) {
            log.debug("Ignoring attendance event {} with non-finalized status {}", event.eventId(), status);
            processedEventRepository.save(new ProcessedEvent(event.eventId(), now));
            return;
        }

        UUID employeeRef = UUID.fromString(String.valueOf(event.payload().get("employeeId")));
        UUID attendanceRef = UUID.fromString(String.valueOf(event.payload().get("attendanceId")));
        LocalDate workDate = LocalDate.parse(String.valueOf(event.payload().get("workDate")));

        attendanceInputRepository.findByEmployeeRefAndWorkDate(employeeRef, workDate)
                .ifPresentOrElse(
                        existing -> log.debug("Attendance input for employee {} on {} already recorded; leaving unchanged.", employeeRef, workDate),
                        () -> attendanceInputRepository.save(new PayrollAttendanceInput(UUID.randomUUID(), employeeRef, workDate,
                                attendanceRef, event.eventId(), now)));

        processedEventRepository.save(new ProcessedEvent(event.eventId(), now));
    }
}
