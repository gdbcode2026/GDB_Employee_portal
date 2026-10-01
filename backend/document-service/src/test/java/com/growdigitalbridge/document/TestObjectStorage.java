package com.growdigitalbridge.document;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real, S3-API-compatible server for tests - {@code zenko/cloudserver}, the same publicly
 * pullable image used for local development in {@code infrastructure/docker/compose.yaml} now
 * that MinIO's own image has been withdrawn from public pull on both Docker Hub and Quay.io.
 * Exercised through the exact same {@link com.growdigitalbridge.document.storage.ObjectStorageClient}
 * interface and AWS S3 SDK the application uses in every environment; nothing about {@code
 * S3ObjectStorageClient} is aware which S3-compatible server is on the other end - it does not
 * even receive {@code initialBuckets}-style help here, so this also exercises the application's
 * own {@code ensureBucketExists()} bucket-creation path against the real target image. Any Spring
 * context that includes {@code S3ObjectStorageClient} must have one of these running before
 * startup, since its {@code @PostConstruct} bucket check needs a live endpoint - exactly like the
 * platform-wide convention of adding a {@code RabbitMQContainer} whenever a context includes a
 * real {@code @RabbitListener} bean.
 */
public final class TestObjectStorage {

    public static final String BUCKET = "test-documents";
    public static final String ACCESS_KEY = "test-access-key";
    public static final String SECRET_KEY = "test-secret-key";

    private TestObjectStorage() { }

    public static GenericContainer<?> container() {
        return new GenericContainer<>(DockerImageName.parse("zenko/cloudserver:latest"))
                .withExposedPorts(8000)
                .withEnv("SCALITY_ACCESS_KEY_ID", ACCESS_KEY)
                .withEnv("SCALITY_SECRET_ACCESS_KEY", SECRET_KEY)
                .waitingFor(Wait.forLogMessage(".*\"message\":\"server started\".*\\n", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    public static void registerProperties(DynamicPropertyRegistry registry, GenericContainer<?> container) {
        registry.add("gdb.storage.endpoint", () -> "http://" + container.getHost() + ":" + container.getMappedPort(8000));
        registry.add("gdb.storage.region", () -> "us-east-1");
        registry.add("gdb.storage.access-key", () -> ACCESS_KEY);
        registry.add("gdb.storage.secret-key", () -> SECRET_KEY);
        registry.add("gdb.storage.bucket", () -> BUCKET);
        registry.add("gdb.storage.connection-timeout", () -> "PT10S");
        registry.add("gdb.storage.download-url-expiry", () -> "PT5M");
    }
}
