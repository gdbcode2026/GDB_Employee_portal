package com.growdigitalbridge.project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Absent from committed configuration by design: deployment supplies these only after
 * GDB selects an OIDC provider. Their absence keeps {@link SecurityConfig}'s JWT decoder
 * bean uninstantiated, so every protected endpoint stays denied until they are set.
 */
@ConfigurationProperties(prefix = "gdb.security.oidc")
public record ProjectOidcProperties(String issuerUri, String audience) { }
