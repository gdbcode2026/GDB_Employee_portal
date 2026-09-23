package com.growdigitalbridge.asset.messaging;

import com.growdigitalbridge.asset.domain.ProcessedEvent;
import com.growdigitalbridge.asset.repository.ProcessedEventRepository;
import com.growdigitalbridge.asset.service.AssetRequestService;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches the two events documented for Asset Service on {@code event.eventType()}.
 *
 * {@code employee.deactivated.v1}: MICROSERVICES.md names Asset as a "lifecycle" consumer but
 * specifies no concrete action, and no custody-reclaim-on-deactivation policy is documented
 * anywhere - inventing one here would be exactly the kind of undocumented workflow this
 * increment must not add. Handling is intentionally limited to idempotent receipt, exactly
 * like Document Service's handling of the same event.
 *
 * {@code workflow.completed.v1}: applies the terminal outcome to the matching AssetRequest,
 * per the explicit instruction to integrate through this contract only. Only events whose
 * {@code subjectType} is {@code ASSET_REQUEST} are acted on; every other subject type is
 * ignored here since it belongs to a different owning domain.
 */
@Component
public class AssetEventListener {

    private static final Logger log = LoggerFactory.getLogger(AssetEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final AssetRequestService assetRequestService;

    public AssetEventListener(ProcessedEventRepository processedEventRepository, AssetRequestService assetRequestService) {
        this.processedEventRepository = processedEventRepository;
        this.assetRequestService = assetRequestService;
    }

    @RabbitListener(queues = "asset.domain-events.v1")
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }

        if ("workflow.completed.v1".equals(event.eventType())) {
            handleWorkflowCompleted(event);
        } else {
            log.info("Received {} for aggregate {} (correlation {})", event.eventType(), event.aggregateId(), event.correlationId());
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }

    private void handleWorkflowCompleted(DomainEvent event) {
        Object subjectType = event.payload().get("subjectType");
        if (!"ASSET_REQUEST".equals(subjectType)) {
            return;
        }
        UUID subjectRef = UUID.fromString(String.valueOf(event.payload().get("subjectRef")));
        UUID workflowId = UUID.fromString(String.valueOf(event.payload().get("workflowId")));
        String outcome = String.valueOf(event.payload().get("outcome"));
        assetRequestService.applyWorkflowOutcome(subjectRef, workflowId, outcome, "workflow-completed-event", Instant.now());
    }
}
