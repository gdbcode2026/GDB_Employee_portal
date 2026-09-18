package com.growdigitalbridge.leave.api;

import com.growdigitalbridge.leave.api.dto.LeaveBalanceDtos;
import com.growdigitalbridge.leave.security.CurrentActor;
import com.growdigitalbridge.leave.service.LeaveBalanceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leave/balances")
class LeaveBalanceController {

    private final LeaveBalanceService service;

    LeaveBalanceController(LeaveBalanceService service) {
        this.service = service;
    }

    @GetMapping("/me")
    List<LeaveBalanceDtos.Response> me(Authentication authentication) {
        return service.listSelf(authentication);
    }

    @GetMapping
    List<LeaveBalanceDtos.Response> list(Authentication authentication, @RequestParam(required = false) UUID employeeId) {
        return service.list(authentication, employeeId);
    }

    @PostMapping
    ResponseEntity<LeaveBalanceDtos.Response> allocate(@Valid @RequestBody LeaveBalanceDtos.AllocateRequest request) {
        LeaveBalanceDtos.Response created = service.allocate(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/leave/balances/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    LeaveBalanceDtos.Response adjust(@PathVariable UUID id, @Valid @RequestBody LeaveBalanceDtos.AdjustRequest request) {
        return service.adjust(id, request, CurrentActor.resolve());
    }
}
