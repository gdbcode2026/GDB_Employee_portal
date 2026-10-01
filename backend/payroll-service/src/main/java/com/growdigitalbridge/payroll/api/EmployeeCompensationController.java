package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.EmployeeCompensationDtos;
import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.EmployeeCompensationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Employee Compensation Management (item 1/5) - HR/Finance (payroll.process/payroll.read.all) only, never employee self-service. */
@RestController
@RequestMapping("/api/v1/payroll/compensations")
class EmployeeCompensationController {

    private final EmployeeCompensationService service;

    EmployeeCompensationController(EmployeeCompensationService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<EmployeeCompensationDtos.Response> create(@Valid @RequestBody EmployeeCompensationDtos.CreateRequest request) {
        EmployeeCompensationDtos.Response created = service.create(request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/payroll/compensations/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    EmployeeCompensationDtos.Response getById(@PathVariable UUID id) {
        return service.getById(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping
    PageResponse<EmployeeCompensationDtos.Response> listByEmployee(@RequestParam UUID employeeRef,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size,
                                                                     @RequestParam(required = false) String sort) {
        return service.listByEmployee(employeeRef, PagingSupport.of(page, size, sort, "effectiveFrom"));
    }

    @PatchMapping("/{id}")
    EmployeeCompensationDtos.Response update(@PathVariable UUID id, @Valid @RequestBody EmployeeCompensationDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
