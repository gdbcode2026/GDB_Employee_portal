package com.growdigitalbridge.document;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(DocumentApplication.class.getSimpleName()).isEqualTo("DocumentApplication");
    }
}
