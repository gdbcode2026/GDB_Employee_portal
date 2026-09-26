package com.growdigitalbridge.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URL of the Employee Service, called to resolve the active-employee set for a new run (Section I). */
@ConfigurationProperties(prefix = "gdb.clients.employee-service")
public record EmployeeClientProperties(String baseUrl) { }
