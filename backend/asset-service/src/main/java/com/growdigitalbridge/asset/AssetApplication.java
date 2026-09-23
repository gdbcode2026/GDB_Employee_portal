package com.growdigitalbridge.asset;

import com.growdigitalbridge.asset.config.AssetOidcProperties;
import com.growdigitalbridge.asset.config.EmployeeClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({AssetOidcProperties.class, EmployeeClientProperties.class})
@EnableScheduling
public class AssetApplication {
    public static void main(String[] args) { SpringApplication.run(AssetApplication.class, args); }
}
