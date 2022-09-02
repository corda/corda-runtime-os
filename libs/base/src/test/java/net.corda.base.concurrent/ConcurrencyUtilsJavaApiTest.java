package net.corda.base.concurrent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConcurrencyUtilsJavaApiTest {

    @Test
    public void getOrThrow() throws InterruptedException, TimeoutException, ExecutionException {
        @SuppressWarnings("unchecked")
        final Future<Integer> integerFuture = mock(Future.class);
        final Integer integer = 5;
        when(integerFuture.get()).thenReturn(integer);

        final Integer integerTest = ConcurrencyUtils.getOrThrow(integerFuture);

        Assertions.assertThat(integerTest).isNotNull();
        Assertions.assertThat(integerTest).isEqualTo(integer);
    }

    @Test
    public void getOrThrow_withDuration() throws InterruptedException, TimeoutException, ExecutionException {
        @SuppressWarnings("unchecked")
        final Future<Integer> integerFuture = mock(Future.class);
        final Integer integer = 5;
        final Duration duration = Duration.ofHours(5L);
        when(integerFuture.get(duration.toNanos(), TimeUnit.NANOSECONDS)).thenReturn(integer);

        final Integer integerTest = ConcurrencyUtils.getOrThrow(integerFuture, duration);

        Assertions.assertThat(integerTest).isNotNull();
        Assertions.assertThat(integerTest).isEqualTo(integer);
    }
}
