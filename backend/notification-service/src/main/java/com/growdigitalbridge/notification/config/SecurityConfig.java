package com.growdigitalbridge.notification.config;
import org.springframework.context.annotation.*; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.web.SecurityFilterChain;
@Configuration class SecurityConfig { @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**", "/actuator/info").permitAll().anyRequest().denyAll()).build(); } }
