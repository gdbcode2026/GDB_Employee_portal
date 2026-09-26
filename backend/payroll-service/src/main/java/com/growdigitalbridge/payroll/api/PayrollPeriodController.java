package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.PayrollPeriodService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/periods")
class PayrollPeriodController {

    private final PayrollPeriodService service;

    PayrollPeriodController(PayrollPeriodService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<PayrollPeriodDtos.Response> create(@Valid @RequestBody PayrollPeriodDtos.CreateRequest request) {
        PayrollPeriodDtos.Response created = service.create(request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/payroll/periods/" + created.id())).body(created);
    }

    @GetMapping
    List<PayrollPeriodDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    PayrollPeriodDtos.Response getById(@PathVariable UUID id) {
        return service.getById(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
