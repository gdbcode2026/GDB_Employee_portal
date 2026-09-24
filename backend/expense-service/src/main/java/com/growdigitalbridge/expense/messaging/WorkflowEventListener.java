package com.growdigitalbridge.expense.messaging;

import com.growdigitalbridge.expense.domain.ProcessedEvent;
import com.growdigitalbridge.expense.repository.ProcessedEventRepository;
import com.growdigitalbridge.expense.service.ExpenseClaimService;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a terminal {@code workflow.completed.v1} outcome to the matching ExpenseClaim, per
 * the explicit instruction to integrate through this contract only - mirroring Asset Service's
 * identical treatment of AssetRequest. Only events whose {@code subjectType} is {@code EXPENSE}
 * are acted on; every other subject type belongs to a different owning domain and is ignored.
 * Idempotent via the inbox check here and the "only while still SUBMITTED" state guard inside
 * {@link ExpenseClaimService#applyWorkflowOutcome}.
 */
@Component
public class WorkflowEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ExpenseClaimService expenseClaimService;

    public WorkflowEventListener(ProcessedEventRepository processedEventRepository, ExpenseClaimService expenseClaimService) {
        this.processedEventRepository = processedEventRepository;
        this.expenseClaimService = expenseClaimService;
    }

    @RabbitListener(queues = "expense.workflow-events.v1")
    @Transactional
    public void onWorkflowCompleted(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }

        Object subjectType = event.payload().get("subjectType");
        if ("EXPENSE".equals(subjectType)) {
            UUID subjectRef = UUID.fromString(String.valueOf(event.payload().get("subjectRef")));
            UUID workflowId = UUID.fromString(String.valueOf(event.payload().get("workflowId")));
            String outcome = String.valueOf(event.payload().get("outcome"));
            expenseClaimService.applyWorkflowOutcome(subjectRef, workflowId, outcome, "workflow-completed-event",
                    Instant.now(), event.correlationId());
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
