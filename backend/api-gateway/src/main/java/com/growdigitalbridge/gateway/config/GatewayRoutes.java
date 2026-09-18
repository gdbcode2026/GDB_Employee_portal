package com.growdigitalbridge.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gdb.gateway")
public record GatewayRoutes(String auditServiceUri, String notificationServiceUri, String organizationServiceUri,
                             String employeeServiceUri, String attendanceServiceUri, String leaveServiceUri) { }
