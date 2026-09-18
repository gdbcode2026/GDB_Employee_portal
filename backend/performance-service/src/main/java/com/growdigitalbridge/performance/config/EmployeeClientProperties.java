package com.growdigitalbridge.performance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URL of the Employee Service, called directly to resolve the caller's own employee reference. */
@ConfigurationProperties(prefix = "gdb.clients.employee-service")
public record EmployeeClientProperties(String baseUrl) { }
