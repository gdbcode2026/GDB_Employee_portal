package com.growdigitalbridge.leave;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(LeaveApplication.class.getSimpleName()).isEqualTo("LeaveApplication");
    }
}
