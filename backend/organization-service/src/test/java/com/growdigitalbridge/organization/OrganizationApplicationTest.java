package com.growdigitalbridge.organization;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(OrganizationApplication.class.getSimpleName()).isEqualTo("OrganizationApplication");
    }
}
