package com.growdigitalbridge.project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URL of the Organization Service, called directly (not via the gateway) to resolve team scope. */
@ConfigurationProperties(prefix = "gdb.clients.organization-service")
public record OrganizationClientProperties(String baseUrl) { }
