package com.growdigitalbridge.employee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.growdigitalbridge.employee.config.EmployeeOidcProperties;
import com.growdigitalbridge.employee.config.OrganizationClientProperties;

@SpringBootApplication
@EnableConfigurationProperties({EmployeeOidcProperties.class, OrganizationClientProperties.class})
@EnableScheduling
public class EmployeeApplication {
    public static void main(String[] args) { SpringApplication.run(EmployeeApplication.class, args); }
}
