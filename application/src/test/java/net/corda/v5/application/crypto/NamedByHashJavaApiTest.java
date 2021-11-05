package net.corda.v5.application.crypto;

import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NamedByHashJavaApiTest {

    private final NamedByHash namedByHash = mock(NamedByHash.class);

    @Test
    public void getId() {
        SecureHash test = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
        when(namedByHash.getId()).thenReturn(test);

        SecureHash result = namedByHash.getId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }
}
