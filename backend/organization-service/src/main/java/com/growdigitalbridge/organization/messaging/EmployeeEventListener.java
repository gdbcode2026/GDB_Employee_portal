package com.growdigitalbridge.organization.messaging;

import com.growdigitalbridge.organization.domain.ProcessedEvent;
import com.growdigitalbridge.organization.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records receipt of Employee lifecycle events. Organization owns no employee profile data
 * (see MICROSERVICES.md's boundary rules) and no business rule requiring a local employee
 * projection is documented, so handling here is intentionally limited to idempotent receipt -
 * inventing a projection/cache would be adding business logic nothing has specified.
 */
@Component
public class EmployeeEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventListener.class);

    private final ProcessedEventRepository processedEventRepository;

    public EmployeeEventListener(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "organization.employee-events.v1")
    @Transactional
    public void onEmployeeEvent(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }
        log.info("Received {} for employee {} (correlation {})", event.eventType(), event.aggregateId(), event.correlationId());
        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
