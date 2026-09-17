package com.growdigitalbridge.organization.api;

import com.growdigitalbridge.organization.api.dto.OrganizationChartResponse;
import com.growdigitalbridge.organization.service.OrganizationChartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/chart")
class OrganizationChartController {

    private final OrganizationChartService service;

    OrganizationChartController(OrganizationChartService service) {
        this.service = service;
    }

    @GetMapping
    OrganizationChartResponse chart() {
        return service.buildChart();
    }
}
