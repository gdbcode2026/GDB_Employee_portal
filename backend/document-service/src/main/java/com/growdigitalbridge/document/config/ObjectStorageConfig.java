package com.growdigitalbridge.document.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Builds the S3-compatible client against whatever {@code gdb.storage.endpoint} names - MinIO
 * locally, any S3-compatible provider elsewhere. {@code forcePathStyle} is required for MinIO
 * (it does not support virtual-hosted-style addressing without extra DNS setup). No credential
 * is hard-coded: both keys come from {@link ObjectStorageProperties}, itself sourced from
 * environment configuration.
 */
@Configuration
public class ObjectStorageConfig {

    @Bean
    S3Client s3Client(ObjectStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(properties.connectionTimeout()))
                .build();
    }

    @Bean
    S3Presigner s3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private StaticCredentialsProvider credentials(ObjectStorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
