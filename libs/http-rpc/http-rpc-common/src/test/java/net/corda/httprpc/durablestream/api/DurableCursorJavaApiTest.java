package net.corda.httprpc.durablestream.api;

import kotlin.Unit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

public class DurableCursorJavaApiTest {

    @SuppressWarnings("unchecked")
    private final DurableCursor<Integer> durableCursor = Mockito.mock(DurableCursor.class);

    @Test
    public void currentPosition() {
        Mockito.when(durableCursor.getCurrentPosition()).thenReturn(5L);

        final Long aLong = durableCursor.getCurrentPosition();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }

    @Test
    public void seek() {
        durableCursor.seek(5L);

        Mockito.verify(durableCursor, Mockito.times(1)).seek(5L);
    }

    @Test
    public void reset() {
        durableCursor.reset();

        Mockito.verify(durableCursor, Mockito.times(1)).reset();
    }

    @Test
    public void asyncCommit() {
        @SuppressWarnings("unchecked")
        CompletableFuture<Unit> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(durableCursor.asyncCommit(5L)).thenReturn(future);

        CompletableFuture<Unit> futureTest = durableCursor.asyncCommit(5L);

        Assertions.assertThat(futureTest).isNotNull();
        Assertions.assertThat(futureTest).isEqualTo(future);
    }

    @Test
    public void commit_withLong() {
        durableCursor.commit(5L);

        Mockito.verify(durableCursor, Mockito.times(1)).commit(5L);
    }

    @Test
    public void commit_withPollResult() {
        @SuppressWarnings("unchecked")
        final Cursor.PollResult<Integer> pollResult = Mockito.mock(Cursor.PollResult.class);
        durableCursor.commit(pollResult);

        Mockito.verify(durableCursor, Mockito.times(1)).commit(pollResult);
    }

    @Test
    public void positionManager() {
        final PositionManager positionManager = Mockito.mock(PositionManager.class);
        Mockito.when(durableCursor.getPositionManager()).thenReturn(positionManager);

        final PositionManager positionManagerTest = durableCursor.getPositionManager();

        Assertions.assertThat(positionManagerTest).isNotNull();
        Assertions.assertThat(positionManagerTest).isEqualTo(positionManager);
    }
}
