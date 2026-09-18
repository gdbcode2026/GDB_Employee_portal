package com.growdigitalbridge.project;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(ProjectApplication.class.getSimpleName()).isEqualTo("ProjectApplication");
    }
}
