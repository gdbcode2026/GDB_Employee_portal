package com.growdigitalbridge.gateway.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "gdb.security.oidc")
public record GatewayOidcProperties(String issuerUri, String audience) { }
