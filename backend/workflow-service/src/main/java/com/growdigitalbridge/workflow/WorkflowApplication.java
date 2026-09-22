package com.growdigitalbridge.workflow;

import com.growdigitalbridge.workflow.config.EmployeeClientProperties;
import com.growdigitalbridge.workflow.config.OrganizationClientProperties;
import com.growdigitalbridge.workflow.config.WorkflowOidcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({WorkflowOidcProperties.class, OrganizationClientProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class WorkflowApplication {
    public static void main(String[] args) { SpringApplication.run(WorkflowApplication.class, args); }
}
