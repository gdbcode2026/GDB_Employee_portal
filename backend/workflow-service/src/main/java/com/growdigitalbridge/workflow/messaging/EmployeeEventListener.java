package com.growdigitalbridge.workflow.messaging;

import com.growdigitalbridge.workflow.domain.ProcessedEvent;
import com.growdigitalbridge.workflow.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records receipt of {@code EMPLOYEE_DEACTIVATED}. COMMUNICATION.md names Workflow as a
 * consumer of this event but specifies no concrete action, and no documented rule says an
 * in-flight approval instance/task should be cancelled, reassigned, or otherwise altered when
 * a participant is deactivated (WORKFLOWS.md leaves delegation/escalation policy as an open
 * GDB decision) - inventing such a rule here would be exactly the kind of undocumented
 * workflow-behavior change this fix must not add. Handling is intentionally limited to
 * idempotent receipt, exactly like Document and Asset Service's handling of the same event.
 */
@Component
public class EmployeeEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventListener.class);

    private final ProcessedEventRepository processedEventRepository;

    public EmployeeEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "workflow.employee-events.v1")
    @Transactional
    public void onEmployeeDeactivated(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }
        log.info("Received {} for employee {} (correlation {})", event.eventType(), event.aggregateId(), event.correlationId());
        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
