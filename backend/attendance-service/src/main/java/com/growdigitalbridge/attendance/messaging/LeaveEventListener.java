package com.growdigitalbridge.attendance.messaging;

import com.growdigitalbridge.attendance.domain.ProcessedEvent;
import com.growdigitalbridge.attendance.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records receipt of {@code LEAVE_APPROVED}. No documented business rule says approved leave
 * should automatically alter attendance records (docs/workflows/WORKFLOWS.md does not specify
 * this, and no such effect appears in docs/database/DATABASE.md's Attendance model), so
 * inventing an automatic attendance projection here would be adding an undocumented business
 * policy. Handling is intentionally limited to idempotent receipt, exactly like Organization
 * Service's handling of Employee lifecycle events.
 */
@Component
public class LeaveEventListener {

    private static final Logger log = LoggerFactory.getLogger(LeaveEventListener.class);

    private final ProcessedEventRepository processedEventRepository;

    public LeaveEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "attendance.leave-events.v1")
    @Transactional
    public void onLeaveApproved(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }
        log.info("Received {} for employee {} (correlation {})", event.eventType(), event.aggregateId(), event.correlationId());
        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
