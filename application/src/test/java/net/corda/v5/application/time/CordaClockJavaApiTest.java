package net.corda.v5.application.time;

import net.corda.v5.application.time.CordaClock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CordaClockJavaApiTest {

    private final CordaClock cordaClock = mock(CordaClock.class);

    @Test
    public void instant() {
        final Instant instant = Instant.now();
        when(cordaClock.instant()).thenReturn(instant);

        final Instant instantTest = cordaClock.instant();

        Assertions.assertThat(instantTest).isNotNull();
        Assertions.assertThat(instantTest).isEqualTo(instant);
        verify(cordaClock, times(1)).instant();
    }
}
