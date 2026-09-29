package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.PayslipService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Section O's three documented payslip endpoints - no others exist. */
@RestController
@RequestMapping("/api/v1/payroll/payslips")
class PayslipController {

    private final PayslipService service;

    PayslipController(PayslipService service) {
        this.service = service;
    }

    @GetMapping("/me")
    PageResponse<PayslipDtos.Summary> mine(@RequestParam(required = false) UUID periodId,
                                            @RequestParam(required = false) Integer financialYearStart,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String sort) {
        return service.listMine(periodId, financialYearStart, PagingSupport.of(page, size, sort, "generatedAt"));
    }

    @GetMapping("/{id}")
    PayslipDtos.Detail getById(@PathVariable UUID id, Authentication authentication) {
        return service.getById(id, authentication, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping("/{id}/download")
    PayslipDtos.DownloadResponse download(@PathVariable UUID id, Authentication authentication) {
        return service.download(id, authentication, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
