package com.growdigitalbridge.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.audit.repository.AuditEntryRepository;
import com.growdigitalbridge.audit.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditEntryRepository auditEntryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AuditEventListener listener() {
        return new AuditEventListener(auditEntryRepository, processedEventRepository, objectMapper);
    }

    private DomainEvent event(UUID eventId, String eventType, UUID aggregateId) {
        return new DomainEvent(eventId, eventType, 1, Instant.now(), UUID.randomUUID(), "employee-service",
                aggregateId, Map.of("employeeId", aggregateId.toString()));
    }

    @Test
    void recordsAuditEntryAndMarksProcessedForANewEvent() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        listener().onDomainEvent(event(eventId, "employee.created.v1", aggregateId));

        verify(auditEntryRepository).save(argThat(entry ->
                entry.getAction().equals("employee.created.v1")
                        && entry.getResourceType().equals("Employee")
                        && entry.getResourceRef().equals(aggregateId.toString())
                        && entry.getOutcome().equals("SUCCESS")));
        verify(processedEventRepository).save(argThat(p -> p.getEventId().equals(eventId)));
    }

    @Test
    void skipsAnAlreadyProcessedEventWithoutInsertingAgain() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        listener().onDomainEvent(event(eventId, "employee.created.v1", UUID.randomUUID()));

        verify(auditEntryRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void throwsOnMalformedEventTypeSoTheRetryMechanismCanEngage() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> listener().onDomainEvent(event(eventId, "", UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class);
    }
}
