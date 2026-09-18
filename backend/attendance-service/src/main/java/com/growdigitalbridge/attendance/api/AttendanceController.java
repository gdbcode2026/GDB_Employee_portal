package com.growdigitalbridge.attendance.api;

import com.growdigitalbridge.attendance.api.dto.AttendanceDtos;
import com.growdigitalbridge.attendance.api.dto.PageResponse;
import com.growdigitalbridge.attendance.domain.AttendanceStatus;
import com.growdigitalbridge.attendance.security.CurrentActor;
import com.growdigitalbridge.attendance.security.CurrentCorrelation;
import com.growdigitalbridge.attendance.service.AttendanceService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
class AttendanceController {

    private final AttendanceService service;

    AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping("/me")
    PageResponse<AttendanceDtos.Response> me(Authentication authentication,
                                              @RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam(required = false) AttendanceStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String sort) {
        return service.listSelf(authentication, from, to, status, PagingSupport.of(page, size, sort, "workDate"));
    }

    @GetMapping
    PageResponse<AttendanceDtos.Response> list(Authentication authentication,
                                                @RequestParam(required = false) UUID employeeId,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) AttendanceStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return service.list(authentication, employeeId, from, to, status, PagingSupport.of(page, size, sort, "workDate"));
    }

    @PostMapping("/check-ins")
    AttendanceDtos.Response checkIn(Authentication authentication) {
        return service.checkIn(authentication, CurrentActor.resolve());
    }

    @PostMapping("/check-outs")
    AttendanceDtos.Response checkOut(Authentication authentication) {
        return service.checkOut(authentication, CurrentActor.resolve());
    }

    @PostMapping("/{id}/finalize")
    AttendanceDtos.Response finalizeRecord(@PathVariable UUID id, Authentication authentication) {
        return service.finalizeRecord(id, authentication, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
