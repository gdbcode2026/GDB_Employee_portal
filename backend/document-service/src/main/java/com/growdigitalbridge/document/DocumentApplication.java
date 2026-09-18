package com.growdigitalbridge.document;

import com.growdigitalbridge.document.config.DocumentOidcProperties;
import com.growdigitalbridge.document.config.EmployeeClientProperties;
import com.growdigitalbridge.document.config.OrganizationClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({DocumentOidcProperties.class, OrganizationClientProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class DocumentApplication {
    public static void main(String[] args) { SpringApplication.run(DocumentApplication.class, args); }
}
