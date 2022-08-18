package net.corda.v5.ledger.common.transaction;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PrivacySaltJavaApiTest {

    private final PrivacySalt privacySalt = mock(PrivacySalt.class);

    @Test
    public void get() {
        byte[] test = "123".getBytes();
        when(privacySalt.getBytes()).thenReturn(test);

        byte[] result = privacySalt.getBytes();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(privacySalt, times(1)).getBytes();
    }
}
