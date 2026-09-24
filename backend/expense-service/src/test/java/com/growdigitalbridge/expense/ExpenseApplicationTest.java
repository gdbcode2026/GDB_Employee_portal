package com.growdigitalbridge.expense;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExpenseApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(ExpenseApplication.class.getSimpleName()).isEqualTo("ExpenseApplication");
    }
}
