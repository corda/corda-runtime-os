package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class TokenizableAssetInfoJavaApiTest {

    @Test
    public void displayTokenSize() {
        final TestAsset testAsset = new TestAsset();
        final BigDecimal bigDecimal = new BigDecimal("100");

        Assertions.assertThat(testAsset.getDisplayTokenSize()).isEqualTo(bigDecimal);
    }

    static class TestAsset implements TokenizableAssetInfo {
        @NotNull
        @Override
        public BigDecimal getDisplayTokenSize() {
            return new BigDecimal("100");
        }
    }
}
