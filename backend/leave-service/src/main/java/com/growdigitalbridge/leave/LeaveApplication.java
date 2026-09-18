package com.growdigitalbridge.leave;

import com.growdigitalbridge.leave.config.EmployeeClientProperties;
import com.growdigitalbridge.leave.config.LeaveOidcProperties;
import com.growdigitalbridge.leave.config.OrganizationClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({LeaveOidcProperties.class, OrganizationClientProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class LeaveApplication {
    public static void main(String[] args) { SpringApplication.run(LeaveApplication.class, args); }
}
