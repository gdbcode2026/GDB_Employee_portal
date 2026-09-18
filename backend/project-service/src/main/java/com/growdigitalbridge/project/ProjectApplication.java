package com.growdigitalbridge.project;

import com.growdigitalbridge.project.config.EmployeeClientProperties;
import com.growdigitalbridge.project.config.OrganizationClientProperties;
import com.growdigitalbridge.project.config.ProjectOidcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({ProjectOidcProperties.class, OrganizationClientProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class ProjectApplication {
    public static void main(String[] args) { SpringApplication.run(ProjectApplication.class, args); }
}
