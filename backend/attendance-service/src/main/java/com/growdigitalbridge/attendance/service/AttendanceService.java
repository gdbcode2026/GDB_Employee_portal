package com.growdigitalbridge.attendance.service;

import com.growdigitalbridge.attendance.api.dto.AttendanceDtos;
import com.growdigitalbridge.attendance.api.dto.PageResponse;
import com.growdigitalbridge.attendance.domain.AttendanceRecord;
import com.growdigitalbridge.attendance.domain.AttendanceStatus;
import com.growdigitalbridge.attendance.repository.AttendanceRecordRepository;
import com.growdigitalbridge.attendance.service.exception.ConflictException;
import com.growdigitalbridge.attendance.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.attendance.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns check-in/check-out and finalization. Work date and check-in/check-out instants are
 * always the server clock (UTC) - never a client-supplied timestamp - so a record cannot be
 * backdated or forged through this API; the only path to a different time is an explicit,
 * audited regularization request (see {@link RegularizationService}).
 */
@Service
public class AttendanceService {

    private final AttendanceRecordRepository repository;
    private final AttendanceAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public AttendanceService(AttendanceRecordRepository repository, AttendanceAccessGuard accessGuard,
                              OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public AttendanceDtos.Response checkIn(Authentication authentication, String actor) {
        UUID employeeRef = resolveSelfOrThrow(authentication);
        LocalDate workDate = LocalDate.now(ZoneOffset.UTC);
        if (repository.findByEmployeeRefAndWorkDate(employeeRef, workDate).isPresent()) {
            // Natural idempotency guard: a retried/duplicate check-in for the same day is
            // rejected rather than creating a second record (see docs/api/API.md's
            // "idempotency key... validate active day" requirement).
            throw new ConflictException("Already checked in for " + workDate + ".");
        }
        Instant now = Instant.now();
        AttendanceRecord record = new AttendanceRecord(UUID.randomUUID(), employeeRef, workDate, now, actor, now);
        repository.save(record);
        return toResponse(record);
    }

    @Transactional
    public AttendanceDtos.Response checkOut(Authentication authentication, String actor) {
        UUID employeeRef = resolveSelfOrThrow(authentication);
        LocalDate workDate = LocalDate.now(ZoneOffset.UTC);
        AttendanceRecord record = repository.findByEmployeeRefAndWorkDate(employeeRef, workDate)
                .orElseThrow(() -> new ConflictException("No open check-in was found for " + workDate + "."));
        if (record.getCheckOutAt() != null) {
            throw new ConflictException("Already checked out for " + workDate + ".");
        }
        record.checkOut(Instant.now(), actor, Instant.now());
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.Response> listSelf(Authentication authentication, LocalDate from, LocalDate to,
                                                            AttendanceStatus status, Pageable pageable) {
        UUID employeeRef = resolveSelfOrThrow(authentication);
        return PageResponse.of(repository.searchForEmployee(employeeRef, from, to, status, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.Response> list(Authentication authentication, UUID employeeId, LocalDate from,
                                                        LocalDate to, AttendanceStatus status, Pageable pageable) {
        AttendanceAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing attendance requires self, team, or all read scope.");
        }
        Page<AttendanceRecord> page = resolvePage(scope, employeeId, from, to, status, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    private Page<AttendanceRecord> resolvePage(AttendanceAccessGuard.ListScope scope, UUID employeeId, LocalDate from,
                                                LocalDate to, AttendanceStatus status, Pageable pageable) {
        if (scope.unrestricted()) {
            return employeeId == null
                    ? repository.searchAll(from, to, status, pageable)
                    : repository.searchWithinScope(java.util.Set.of(employeeId), from, to, status, pageable);
        }
        if (scope.allowedIds().isEmpty()) {
            return Page.empty(pageable);
        }
        if (employeeId != null && !scope.allowedIds().contains(employeeId)) {
            return Page.empty(pageable);
        }
        var effectiveIds = employeeId != null ? java.util.Set.of(employeeId) : scope.allowedIds();
        return repository.searchWithinScope(effectiveIds, from, to, status, pageable);
    }

    @Transactional
    public AttendanceDtos.Response finalizeRecord(UUID id, Authentication authentication, String actor, UUID correlationId) {
        AttendanceRecord record = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record " + id + " was not found."));
        if (!accessGuard.canFinalize(authentication, record.getEmployeeRef())) {
            throw new ResourceNotFoundException("Attendance record " + id + " was not found.");
        }
        if (record.getStatus() == AttendanceStatus.FINALIZED) {
            throw new InvalidLifecycleTransitionException("Attendance record " + id + " is already finalized.");
        }
        record.finalizeRecord(actor, Instant.now());
        outboxEventWriter.write("attendance.finalized.v1", record.getId(), Map.of(
                "attendanceId", record.getId().toString(),
                "employeeId", record.getEmployeeRef().toString(),
                "workDate", record.getWorkDate().toString(),
                "status", record.getStatus().name()), correlationId);
        return toResponse(record);
    }

    private UUID resolveSelfOrThrow(Authentication authentication) {
        return accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
    }

    private AttendanceDtos.Response toResponse(AttendanceRecord record) {
        return new AttendanceDtos.Response(record.getId(), record.getEmployeeRef(), record.getWorkDate(),
                record.getCheckInAt(), record.getCheckOutAt(), record.getStatus());
    }
}
