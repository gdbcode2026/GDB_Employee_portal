package com.growdigitalbridge.attendance;

import com.growdigitalbridge.attendance.config.AttendanceOidcProperties;
import com.growdigitalbridge.attendance.config.EmployeeClientProperties;
import com.growdigitalbridge.attendance.config.OrganizationClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({AttendanceOidcProperties.class, OrganizationClientProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class AttendanceApplication {
    public static void main(String[] args) { SpringApplication.run(AttendanceApplication.class, args); }
}
