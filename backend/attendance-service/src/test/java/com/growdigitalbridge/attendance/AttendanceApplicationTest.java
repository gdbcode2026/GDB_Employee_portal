package com.growdigitalbridge.attendance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AttendanceApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(AttendanceApplication.class.getSimpleName()).isEqualTo("AttendanceApplication");
    }
}
