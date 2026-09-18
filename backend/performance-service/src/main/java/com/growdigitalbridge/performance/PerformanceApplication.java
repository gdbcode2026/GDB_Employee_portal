package com.growdigitalbridge.performance;

import com.growdigitalbridge.performance.config.EmployeeClientProperties;
import com.growdigitalbridge.performance.config.OrganizationClientProperties;
import com.growdigitalbridge.performance.config.PerformanceOidcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({PerformanceOidcProperties.class, OrganizationClientProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class PerformanceApplication {
    public static void main(String[] args) { SpringApplication.run(PerformanceApplication.class, args); }
}
