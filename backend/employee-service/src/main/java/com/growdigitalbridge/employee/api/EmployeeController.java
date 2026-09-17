package com.growdigitalbridge.employee.api;

import com.growdigitalbridge.employee.api.dto.EmployeeDtos;
import com.growdigitalbridge.employee.api.dto.PageResponse;
import com.growdigitalbridge.employee.domain.EmployeeStatus;
import com.growdigitalbridge.employee.security.CurrentActor;
import com.growdigitalbridge.employee.security.CurrentCorrelation;
import com.growdigitalbridge.employee.service.EmployeeService;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/employees")
class EmployeeController {

    private final EmployeeService service;

    EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping("/me")
    EmployeeDtos.Response me(Authentication authentication) {
        return service.getSelf(authentication);
    }

    @PatchMapping("/me")
    EmployeeDtos.Response updateMe(Authentication authentication, @Valid @RequestBody EmployeeDtos.SelfUpdateRequest request) {
        return service.updateSelf(authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping("/{id}")
    EmployeeDtos.Response getById(@PathVariable UUID id, Authentication authentication) {
        return service.getById(id, authentication);
    }

    @GetMapping
    PageResponse<EmployeeDtos.Summary> list(Authentication authentication,
                                             @RequestParam(required = false) EmployeeStatus status,
                                             @RequestParam(required = false) String query,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        return service.list(authentication, status, query, PagingSupport.of(page, size, sort, "lastName"));
    }

    @PostMapping
    ResponseEntity<EmployeeDtos.Response> create(@Valid @RequestBody EmployeeDtos.CreateRequest request) {
        EmployeeDtos.Response created = service.create(request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    EmployeeDtos.Response update(@PathVariable UUID id, @Valid @RequestBody EmployeeDtos.AdminUpdateRequest request) {
        return service.updateByAdmin(id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
