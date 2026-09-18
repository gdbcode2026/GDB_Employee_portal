package com.growdigitalbridge.performance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PerformanceApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(PerformanceApplication.class.getSimpleName()).isEqualTo("PerformanceApplication");
    }
}
