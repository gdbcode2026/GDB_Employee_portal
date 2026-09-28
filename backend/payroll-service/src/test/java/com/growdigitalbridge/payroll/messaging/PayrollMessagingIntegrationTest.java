package com.growdigitalbridge.payroll.messaging;

import com.growdigitalbridge.payroll.repository.PayrollAttendanceInputRepository;
import com.growdigitalbridge.payroll.repository.PayrollLeaveInputRepository;
import com.growdigitalbridge.payroll.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves both payroll-input consumers against a real broker: a real {@code
 * attendance.finalized.v1} message is idempotently recorded as a {@link
 * com.growdigitalbridge.payroll.domain.PayrollAttendanceInput}, a non-finalized status on that
 * same event type is ignored (item 4), and a real {@code leave.approved.v1} message is
 * idempotently recorded as a {@link com.growdigitalbridge.payroll.domain.PayrollLeaveInput}
 * (item 5).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PayrollMessagingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private PayrollAttendanceInputRepository attendanceInputRepository;
    @Autowired private PayrollLeaveInputRepository leaveInputRepository;

    @Test
    void finalizedAttendanceEventIsRecordedIdempotently() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID employeeRef = UUID.randomUUID();
        UUID attendanceRef = UUID.randomUUID();
        LocalDate workDate = LocalDate.now();
        DomainEvent event = new DomainEvent(eventId, "attendance.finalized.v1", 1, Instant.now(), UUID.randomUUID(),
                "attendance-service", attendanceRef, Map.of(
                        "attendanceId", attendanceRef.toString(),
                        "employeeId", employeeRef.toString(),
                        "workDate", workDate.toString(),
                        "status", "FINALIZED"));

        rabbitTemplate.convertAndSend("gdb.domain.events", "attendance.finalized.v1", event);
        rabbitTemplate.convertAndSend("gdb.domain.events", "attendance.finalized.v1", event);

        assertThat(awaitProcessed(eventId)).isTrue();
        Optional<com.growdigitalbridge.payroll.domain.PayrollAttendanceInput> input =
                attendanceInputRepository.findByEmployeeRefAndWorkDate(employeeRef, workDate);
        assertThat(input).isPresent();
        assertThat(input.get().getAttendanceRef()).isEqualTo(attendanceRef);
    }

    @Test
    void nonFinalizedAttendanceEventIsIgnoredButStillMarkedProcessed() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID employeeRef = UUID.randomUUID();
        LocalDate workDate = LocalDate.now().minusDays(1);
        DomainEvent event = new DomainEvent(eventId, "attendance.finalized.v1", 1, Instant.now(), UUID.randomUUID(),
                "attendance-service", UUID.randomUUID(), Map.of(
                        "attendanceId", UUID.randomUUID().toString(),
                        "employeeId", employeeRef.toString(),
                        "workDate", workDate.toString(),
                        "status", "DRAFT"));

        rabbitTemplate.convertAndSend("gdb.domain.events", "attendance.finalized.v1", event);

        assertThat(awaitProcessed(eventId)).isTrue();
        assertThat(attendanceInputRepository.findByEmployeeRefAndWorkDate(employeeRef, workDate)).isEmpty();
    }

    @Test
    void approvedLeaveEventIsRecordedIdempotently() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID employeeRef = UUID.randomUUID();
        UUID requestRef = UUID.randomUUID();
        DomainEvent event = new DomainEvent(eventId, "leave.approved.v1", 1, Instant.now(), UUID.randomUUID(),
                "leave-service", requestRef, Map.of(
                        "requestId", requestRef.toString(),
                        "employeeId", employeeRef.toString(),
                        "approvedUnits", "2"));

        rabbitTemplate.convertAndSend("gdb.domain.events", "leave.approved.v1", event);
        rabbitTemplate.convertAndSend("gdb.domain.events", "leave.approved.v1", event);

        assertThat(awaitProcessed(eventId)).isTrue();
        Optional<com.growdigitalbridge.payroll.domain.PayrollLeaveInput> input = leaveInputRepository.findByLeaveRequestRef(requestRef);
        assertThat(input).isPresent();
        assertThat(input.get().getApprovedUnits()).isEqualByComparingTo(new BigDecimal("2"));
    }

    private boolean awaitProcessed(UUID eventId) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (processedEventRepository.existsById(eventId)) {
                return true;
            }
            Thread.sleep(300);
        }
        return false;
    }
}
