package com.growdigitalbridge.asset;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AssetApplicationTest {
    @Test void applicationClassIsNamedAsExpected() {
        assertThat(AssetApplication.class.getSimpleName()).isEqualTo("AssetApplication");
    }
}
