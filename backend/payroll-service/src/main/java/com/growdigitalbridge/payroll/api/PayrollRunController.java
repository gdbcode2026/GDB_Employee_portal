package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.PayrollRunService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/payroll/runs")
class PayrollRunController {

    private final PayrollRunService service;

    PayrollRunController(PayrollRunService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<PayrollRunDtos.Response> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return service.list(PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping
    ResponseEntity<PayrollRunDtos.Response> create(@Valid @RequestBody PayrollRunDtos.CreateRequest request) {
        PayrollRunDtos.Response created = service.create(request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/payroll/runs/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    PayrollRunDtos.Response getById(@PathVariable UUID id) {
        return service.getById(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/process")
    PayrollRunDtos.Response process(@PathVariable UUID id) {
        return service.process(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/submit")
    PayrollRunDtos.Response submit(@PathVariable UUID id) {
        return service.submitForApproval(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/approve")
    PayrollRunDtos.Response approve(@PathVariable UUID id) {
        return service.approve(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/reject")
    PayrollRunDtos.Response reject(@PathVariable UUID id) {
        return service.reject(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/finalize")
    PayrollRunDtos.Response finalizeRun(@PathVariable UUID id) {
        return service.finalizeRun(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
