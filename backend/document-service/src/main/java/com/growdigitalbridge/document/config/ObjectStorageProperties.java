package com.growdigitalbridge.document.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Private S3-compatible object storage (MinIO locally) for document binaries
 * (MICROSERVICES.md: "No binary files in database... private object storage"). No credential
 * has a hard-coded value anywhere in code - every field here is sourced from environment
 * configuration, and {@code application.yml} supplies only local-development-only defaults,
 * matching this platform's existing convention for every other credential (see the Postgres/
 * RabbitMQ services' own {@code local-development-only} defaults).
 */
@ConfigurationProperties(prefix = "gdb.storage")
public record ObjectStorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String keyPrefix,
        Duration connectionTimeout,
        Duration downloadUrlExpiry) {
}
