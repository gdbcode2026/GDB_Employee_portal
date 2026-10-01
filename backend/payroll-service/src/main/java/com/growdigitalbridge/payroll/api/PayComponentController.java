package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayComponentDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.PayComponentService;
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

/** Pay Component catalogue structure management (item 2) - HR/Finance only. */
@RestController
@RequestMapping("/api/v1/payroll/pay-components")
class PayComponentController {

    private final PayComponentService service;

    PayComponentController(PayComponentService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<PayComponentDtos.Response> create(@Valid @RequestBody PayComponentDtos.CreateRequest request) {
        PayComponentDtos.Response created = service.create(request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/payroll/pay-components/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    PayComponentDtos.Response getById(@PathVariable UUID id) {
        return service.getById(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping
    PageResponse<PayComponentDtos.Response> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return service.list(PagingSupport.of(page, size, sort, "code"));
    }

    @PatchMapping("/{id}")
    PayComponentDtos.Response update(@PathVariable UUID id, @Valid @RequestBody PayComponentDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
