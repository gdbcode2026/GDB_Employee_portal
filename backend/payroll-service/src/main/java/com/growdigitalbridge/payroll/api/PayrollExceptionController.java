package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayrollExceptionDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.PayrollExceptionService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Payroll exception retrieval/resolution (item 4) - HR/Finance only. */
@RestController
@RequestMapping("/api/v1/payroll/exceptions")
class PayrollExceptionController {

    private final PayrollExceptionService service;

    PayrollExceptionController(PayrollExceptionService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<PayrollExceptionDtos.Response> list(@RequestParam(required = false) UUID runId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) String sort) {
        return service.list(runId, PagingSupport.of(page, size, sort, "detectedAt"));
    }

    @PostMapping("/{id}/resolve")
    PayrollExceptionDtos.Response resolve(@PathVariable UUID id) {
        return service.resolve(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
