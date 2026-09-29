package com.growdigitalbridge.payroll;

import com.growdigitalbridge.payroll.config.DocumentServiceClientProperties;
import com.growdigitalbridge.payroll.config.EmployeeClientProperties;
import com.growdigitalbridge.payroll.config.PayrollOidcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({PayrollOidcProperties.class, EmployeeClientProperties.class, DocumentServiceClientProperties.class})
@EnableScheduling
public class PayrollApplication {
    public static void main(String[] args) { SpringApplication.run(PayrollApplication.class, args); }
}
