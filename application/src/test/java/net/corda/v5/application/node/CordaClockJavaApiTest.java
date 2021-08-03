package net.corda.v5.application.node;

import net.corda.v5.serialization.SerializeAsTokenContext;
import net.corda.v5.serialization.SingletonSerializationToken;
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
    public void toToken() {
        final SerializeAsTokenContext serializeAsTokenContext = mock(SerializeAsTokenContext.class);
        final SingletonSerializationToken singletonSerializationToken =
                SingletonSerializationToken.singletonSerializationToken(SerializeAsTokenContext.class);
        when(cordaClock.toToken(serializeAsTokenContext)).thenReturn(singletonSerializationToken);

        final SingletonSerializationToken singletonSerializationTokenTest = cordaClock.toToken(serializeAsTokenContext);

        Assertions.assertThat(singletonSerializationTokenTest).isNotNull();
        Assertions.assertThat(singletonSerializationTokenTest).isEqualTo(singletonSerializationToken);
        verify(cordaClock, times(1)).toToken(serializeAsTokenContext);
    }

    @Test
    public void instant() {
        final Instant instant = Instant.now();
        when(cordaClock.instant()).thenReturn(instant);

        final Instant instantTest = cordaClock.instant();

        Assertions.assertThat(instantTest).isNotNull();
        Assertions.assertThat(instantTest).isEqualTo(instant);
        verify(cordaClock, times(1)).instant();
    }

    @Test
    public void getZone() {
        final ZoneId zoneId = mock(ZoneId.class);
        when(cordaClock.getZone()).thenReturn(zoneId);

        final ZoneId zoneIdTest = cordaClock.getZone();

        Assertions.assertThat(zoneIdTest).isNotNull();
        Assertions.assertThat(zoneIdTest).isEqualTo(zoneId);
        verify(cordaClock, times(1)).getZone();
    }

    @Test
    public void getDelegateClock() {
        final Clock clock = mock(Clock.class);
        when(cordaClock.getDelegateClock()).thenReturn(clock);

        final Clock clockTest = cordaClock.getDelegateClock();

        Assertions.assertThat(clockTest).isNotNull();
        Assertions.assertThat(clockTest).isEqualTo(clock);
        verify(cordaClock, times(1)).getDelegateClock();
    }
}
