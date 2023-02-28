package net.corda.rest.durablestream.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class CursorJavaApiTest {

    @SuppressWarnings("unchecked")
    private final Cursor<Integer> cursor = Mockito.mock(Cursor.class);
    private final Duration duration = Duration.ofHours(5);
    private final Integer integer = 5;

    @Test
    public void asyncPoll() {
        @SuppressWarnings("unchecked")
        final CompletableFuture<Cursor.PollResult<Integer>> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(cursor.asyncPoll(integer, duration)).thenReturn(future);

        final CompletableFuture<Cursor.PollResult<Integer>> futureTest = cursor.asyncPoll(integer, duration);

        Assertions.assertThat(futureTest).isNotNull();
        Assertions.assertThat(futureTest).isEqualTo(future);
    }

    @Test
    public void poll() {
        @SuppressWarnings("unchecked")
        final Cursor.PollResult<Integer> pollResult = Mockito.mock(Cursor.PollResult.class);
        Mockito.when(cursor.poll(integer, duration)).thenReturn(pollResult);

        final Cursor.PollResult<Integer> pollResultTest = cursor.poll(integer, duration);

        Assertions.assertThat(pollResultTest).isNotNull();
        Assertions.assertThat(pollResultTest).isEqualTo(pollResult);
    }
}
