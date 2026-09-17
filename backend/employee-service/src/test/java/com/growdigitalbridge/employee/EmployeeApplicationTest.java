package com.growdigitalbridge.employee;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmployeeApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(EmployeeApplication.class.getSimpleName()).isEqualTo("EmployeeApplication");
    }
}
