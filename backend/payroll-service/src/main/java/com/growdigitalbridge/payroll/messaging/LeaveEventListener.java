package com.growdigitalbridge.payroll.messaging;

import com.growdigitalbridge.payroll.domain.PayrollLeaveInput;
import com.growdigitalbridge.payroll.domain.ProcessedEvent;
import com.growdigitalbridge.payroll.repository.PayrollLeaveInputRepository;
import com.growdigitalbridge.payroll.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code leave.approved.v1} (Section I, decision 6) and persists the exact fields the
 * event carries ({@code requestId}, {@code employeeId}, {@code approvedUnits}) as a {@link
 * PayrollLeaveInput} - a snapshot for {@code ProrationPolicy} to read later, never a fetch back
 * to Leave Service. Unlike attendance, this event's payload carries no status field at all - Leave
 * Service only ever publishes {@code leave.approved.v1} on approval, and no other variant of this
 * event type exists to defensively filter against, so "ignore non-approved leave" (item 5) is
 * satisfied structurally rather than by a runtime check. Leave Service's event also does not carry
 * the request's date range, so this snapshot is not filtered to a payroll period - a flagged
 * data-availability gap, not an invented assumption. Idempotent via the platform's standard
 * inbox/{@link ProcessedEvent} pattern.
 */
@Component
public class LeaveEventListener {

    private static final Logger log = LoggerFactory.getLogger(LeaveEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final PayrollLeaveInputRepository leaveInputRepository;

    public LeaveEventListener(ProcessedEventRepository processedEventRepository,
                               PayrollLeaveInputRepository leaveInputRepository) {
        this.processedEventRepository = processedEventRepository;
        this.leaveInputRepository = leaveInputRepository;
    }

    @RabbitListener(queues = "payroll.leave-approved.v1")
    @Transactional
    public void onLeaveApproved(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }

        UUID employeeRef = UUID.fromString(String.valueOf(event.payload().get("employeeId")));
        UUID leaveRequestRef = UUID.fromString(String.valueOf(event.payload().get("requestId")));
        BigDecimal approvedUnits = new BigDecimal(String.valueOf(event.payload().get("approvedUnits")));
        Instant now = Instant.now();

        leaveInputRepository.findByLeaveRequestRef(leaveRequestRef)
                .ifPresentOrElse(
                        existing -> log.debug("Leave input for request {} already recorded; leaving unchanged.", leaveRequestRef),
                        () -> leaveInputRepository.save(new PayrollLeaveInput(UUID.randomUUID(), employeeRef, leaveRequestRef,
                                approvedUnits, event.eventId(), now)));

        processedEventRepository.save(new ProcessedEvent(event.eventId(), now));
    }
}
