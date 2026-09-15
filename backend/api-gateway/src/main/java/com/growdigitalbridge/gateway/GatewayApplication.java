package com.growdigitalbridge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.growdigitalbridge.gateway.config.GatewayOidcProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayOidcProperties.class)
public class GatewayApplication {
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
}
