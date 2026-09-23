package com.growdigitalbridge.asset.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient employeeRestClient(EmployeeClientProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
