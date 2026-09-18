package com.growdigitalbridge.attendance.api;

import com.growdigitalbridge.attendance.api.dto.PageResponse;
import com.growdigitalbridge.attendance.api.dto.WfhRequestDtos;
import com.growdigitalbridge.attendance.security.CurrentActor;
import com.growdigitalbridge.attendance.service.WfhRequestService;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/attendance/wfh-requests")
class WfhRequestController {

    private final WfhRequestService service;

    WfhRequestController(WfhRequestService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<WfhRequestDtos.Response> list(Authentication authentication,
                                                @RequestParam(required = false) UUID employeeId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return service.list(authentication, employeeId, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping
    ResponseEntity<WfhRequestDtos.Response> create(Authentication authentication, @Valid @RequestBody WfhRequestDtos.CreateRequest request) {
        WfhRequestDtos.Response created = service.create(authentication, request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/attendance/wfh-requests/" + created.id())).body(created);
    }

    @PostMapping("/{id}/decisions")
    WfhRequestDtos.Response decide(@PathVariable UUID id, Authentication authentication,
                                    @Valid @RequestBody WfhRequestDtos.DecisionRequest request) {
        return service.decide(id, authentication, request, CurrentActor.resolve());
    }
}
