package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IdentifiableExceptionJavaApiTest {

    private final IdentifiableException identifiableException = mock(IdentifiableException.class);

    @Test
    public void getErrorId() {
        Long test = 5L;
        when(identifiableException.getErrorId()).thenReturn(test);

        Long result = identifiableException.getErrorId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }
}
