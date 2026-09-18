package com.growdigitalbridge.document.messaging;

import com.growdigitalbridge.document.domain.ProcessedEvent;
import com.growdigitalbridge.document.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records receipt of {@code EMPLOYEE_DEACTIVATED}. MICROSERVICES.md names Document as a
 * consumer for "lifecycle cleanup" but specifies no concrete action, and no document
 * retention/archival policy is documented anywhere (docs/ARCHITECTURE_REVIEW.md item 3
 * lists retention as still pending a GDB decision) - inventing an auto-archive-on-
 * deactivation policy here would be exactly the kind of undocumented workflow this increment
 * must not add. Handling is intentionally limited to idempotent receipt, exactly like
 * Organization Service's handling of Employee lifecycle events.
 */
@Component
public class EmployeeEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventListener.class);

    private final ProcessedEventRepository processedEventRepository;

    public EmployeeEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "document.employee-events.v1")
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
