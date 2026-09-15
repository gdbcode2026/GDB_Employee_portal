package com.growdigitalbridge.audit;
import java.io.InputStream; import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.assertThat;
class MigrationFoundationTest {
 @Test void auditMigrationIsPackaged() { InputStream migration = getClass().getResourceAsStream("/db/migration/V1__create_audit_foundation.sql"); assertThat(migration).isNotNull(); }
}
