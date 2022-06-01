package net.corda.ledger.common.impl.transactions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class PrivacySaltJavaApiTest {

    private final byte[] bytes = "6D1687C143DF792A011A1E80670A4E4E".getBytes();
    private final PrivacySaltImpl privacySaltC = new PrivacySaltImpl(bytes);

    @Test
    public void getOffset() {
        int result = privacySaltC.getOffset();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(0);
    }

    @Test
    public void getSize() {
        int result = privacySaltC.getSize();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(32);
    }

    @Test
    public void getBytes() {
        byte[] result = privacySaltC.getBytes();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(bytes);
    }
}
