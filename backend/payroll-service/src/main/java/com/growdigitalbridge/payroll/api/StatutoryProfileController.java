package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.StatutoryProfileDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.StatutoryProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Employee Statutory Profile management (item 3) - HR/Finance only, one profile per employee. */
@RestController
@RequestMapping("/api/v1/payroll/statutory-profiles")
class StatutoryProfileController {

    private final StatutoryProfileService service;

    StatutoryProfileController(StatutoryProfileService service) {
        this.service = service;
    }

    @PutMapping("/{employeeRef}")
    StatutoryProfileDtos.Response upsert(@PathVariable UUID employeeRef, @Valid @RequestBody StatutoryProfileDtos.UpsertRequest request) {
        return service.upsert(employeeRef, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping("/{employeeRef}")
    StatutoryProfileDtos.Response getByEmployee(@PathVariable UUID employeeRef) {
        return service.getByEmployee(employeeRef, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
