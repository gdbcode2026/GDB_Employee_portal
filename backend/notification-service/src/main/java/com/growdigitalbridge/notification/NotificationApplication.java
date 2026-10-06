package com.growdigitalbridge.notification;

import com.growdigitalbridge.notification.config.EmployeeClientProperties;
import com.growdigitalbridge.notification.config.NotificationOidcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({NotificationOidcProperties.class, EmployeeClientProperties.class})
public class NotificationApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationApplication.class, args); }
}
