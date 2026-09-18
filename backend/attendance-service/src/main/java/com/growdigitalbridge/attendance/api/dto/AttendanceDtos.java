package com.growdigitalbridge.attendance.api.dto;

import com.growdigitalbridge.attendance.domain.AttendanceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AttendanceDtos {

    private AttendanceDtos() { }

    public record Response(UUID id, UUID employeeRef, LocalDate workDate, Instant checkInAt,
                            Instant checkOutAt, AttendanceStatus status) { }
}
