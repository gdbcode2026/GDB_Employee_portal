package com.growdigitalbridge.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Document Service base URL plus the {@code payroll-service} OAuth2 client-credentials
 * registration (PAYROLL_REQUIREMENTS.md Section N: "Payroll Service authenticates to Document
 * Service using an OAuth2 client-credentials-issued JWT for a dedicated payroll-service
 * client"). {@code tokenUri}/{@code clientId}/{@code clientSecret} are absent from committed
 * configuration by design, exactly like {@link PayrollOidcProperties} - deployment supplies them
 * only once GDB provisions the workload client, and {@link com.growdigitalbridge.payroll.client.WorkloadTokenProvider}
 * fails closed until they are set.
 */
@ConfigurationProperties(prefix = "gdb.clients.document-service")
public record DocumentServiceClientProperties(String baseUrl, String tokenUri, String clientId, String clientSecret) { }
