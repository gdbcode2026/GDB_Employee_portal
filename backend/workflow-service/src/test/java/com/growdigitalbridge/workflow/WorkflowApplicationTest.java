package com.growdigitalbridge.workflow;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(WorkflowApplication.class.getSimpleName()).isEqualTo("WorkflowApplication");
    }
}
