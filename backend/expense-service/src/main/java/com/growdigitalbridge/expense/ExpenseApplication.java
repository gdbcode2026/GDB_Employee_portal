package com.growdigitalbridge.expense;

import com.growdigitalbridge.expense.config.EmployeeClientProperties;
import com.growdigitalbridge.expense.config.ExpenseOidcProperties;
import com.growdigitalbridge.expense.config.OrganizationClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({ExpenseOidcProperties.class, EmployeeClientProperties.class, OrganizationClientProperties.class})
@EnableScheduling
public class ExpenseApplication {
    public static void main(String[] args) { SpringApplication.run(ExpenseApplication.class, args); }
}
