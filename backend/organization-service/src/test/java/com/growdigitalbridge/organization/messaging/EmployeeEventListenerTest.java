package com.growdigitalbridge.organization.messaging;

import com.growdigitalbridge.organization.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeEventListenerTest {

    @Mock
    private ProcessedEventRepository repository;

    private DomainEvent event(UUID eventId) {
        return new DomainEvent(eventId, "employee.created.v1", 1, Instant.now(), UUID.randomUUID(),
                "employee-service", UUID.randomUUID(), Map.of("employeeId", UUID.randomUUID().toString()));
    }

    @Test
    void recordsAPreviouslyUnseenEventAsProcessed() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(false);

        new EmployeeEventListener(repository).onEmployeeEvent(event(eventId));

        verify(repository).save(argThat(saved -> saved.getEventId().equals(eventId)));
    }

    @Test
    void skipsAnAlreadyProcessedEventWithoutReprocessing() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(true);

        new EmployeeEventListener(repository).onEmployeeEvent(event(eventId));

        verify(repository, never()).save(any());
    }
}
