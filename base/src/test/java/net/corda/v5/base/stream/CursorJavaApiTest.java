package net.corda.v5.base.stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CursorJavaApiTest {

    @SuppressWarnings("unchecked")
    private final Cursor<Integer> cursor = mock(Cursor.class);
    private final Duration duration = Duration.ofHours(5);
    private final Integer integer = 5;

    @Test
    public void asyncPoll() {
        @SuppressWarnings("unchecked")
        final CompletableFuture<Cursor.PollResult<Integer>> future = mock(CompletableFuture.class);
        when(cursor.asyncPoll(integer, duration)).thenReturn(future);

        final CompletableFuture<Cursor.PollResult<Integer>> futureTest = cursor.asyncPoll(integer, duration);

        Assertions.assertThat(futureTest).isNotNull();
        Assertions.assertThat(futureTest).isEqualTo(future);
    }

    @Test
    public void poll() {
        @SuppressWarnings("unchecked")
        final Cursor.PollResult<Integer> pollResult = mock(Cursor.PollResult.class);
        when(cursor.poll(integer, duration)).thenReturn(pollResult);

        final Cursor.PollResult<Integer> pollResultTest = cursor.poll(integer, duration);

        Assertions.assertThat(pollResultTest).isNotNull();
        Assertions.assertThat(pollResultTest).isEqualTo(pollResult);
    }
}
