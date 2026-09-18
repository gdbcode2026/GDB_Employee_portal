package com.growdigitalbridge.leave.api;

import com.growdigitalbridge.leave.api.dto.LeaveRequestDtos;
import com.growdigitalbridge.leave.api.dto.PageResponse;
import com.growdigitalbridge.leave.domain.LeaveRequestStatus;
import com.growdigitalbridge.leave.security.CurrentActor;
import com.growdigitalbridge.leave.security.CurrentCorrelation;
import com.growdigitalbridge.leave.service.LeaveRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leave/requests")
class LeaveRequestController {

    private final LeaveRequestService service;

    LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<LeaveRequestDtos.Response> list(Authentication authentication,
                                                  @RequestParam(required = false) UUID employeeId,
                                                  @RequestParam(required = false) LeaveRequestStatus status,
                                                  @RequestParam(required = false) UUID leaveTypeId,
                                                  @RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return service.list(authentication, employeeId, status, leaveTypeId, from, to, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping
    ResponseEntity<LeaveRequestDtos.Response> create(Authentication authentication, @Valid @RequestBody LeaveRequestDtos.CreateRequest request) {
        LeaveRequestDtos.Response created = service.create(authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/leave/requests/" + created.id())).body(created);
    }

    @PostMapping("/{id}/cancel")
    LeaveRequestDtos.Response cancel(@PathVariable UUID id, Authentication authentication) {
        return service.cancel(id, authentication, CurrentActor.resolve());
    }

    @PostMapping("/{id}/decisions")
    LeaveRequestDtos.Response decide(@PathVariable UUID id, Authentication authentication,
                                      @Valid @RequestBody LeaveRequestDtos.DecisionRequest request) {
        return service.decide(id, authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
