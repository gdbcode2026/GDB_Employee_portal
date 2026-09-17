package com.growdigitalbridge.organization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.growdigitalbridge.organization.config.OrganizationOidcProperties;

@SpringBootApplication
@EnableConfigurationProperties(OrganizationOidcProperties.class)
public class OrganizationApplication {
    public static void main(String[] args) { SpringApplication.run(OrganizationApplication.class, args); }
}
