package com.growdigitalbridge.payroll.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient employeeRestClient(EmployeeClientProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    @Bean
    RestClient documentServiceRestClient(DocumentServiceClientProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    /** No base URL: the token endpoint (once configured) is an absolute URI, typically on a different host than Document Service itself. */
    @Bean
    RestClient tokenRestClient() {
        return RestClient.builder().build();
    }
}
