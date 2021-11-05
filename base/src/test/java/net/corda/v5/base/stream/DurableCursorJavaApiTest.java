package net.corda.v5.base.stream;

import kotlin.Unit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DurableCursorJavaApiTest {

    @SuppressWarnings("unchecked")
    private final DurableCursor<Integer> durableCursor = mock(DurableCursor.class);

    @Test
    public void currentPosition() {
        when(durableCursor.getCurrentPosition()).thenReturn(5L);

        final Long aLong = durableCursor.getCurrentPosition();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }

    @Test
    public void seek() {
        durableCursor.seek(5L);

        verify(durableCursor, times(1)).seek(5L);
    }

    @Test
    public void reset() {
        durableCursor.reset();

        verify(durableCursor, times(1)).reset();
    }

    @Test
    public void asyncCommit() {
        @SuppressWarnings("unchecked")
        CompletableFuture<Unit> future = mock(CompletableFuture.class);
        when(durableCursor.asyncCommit(5L)).thenReturn(future);

        CompletableFuture<Unit> futureTest = durableCursor.asyncCommit(5L);

        Assertions.assertThat(futureTest).isNotNull();
        Assertions.assertThat(futureTest).isEqualTo(future);
    }

    @Test
    public void commit_withLong() {
        durableCursor.commit(5L);

        verify(durableCursor, times(1)).commit(5L);
    }

    @Test
    public void commit_withPollResult() {
        @SuppressWarnings("unchecked")
        final Cursor.PollResult<Integer> pollResult = mock(Cursor.PollResult.class);
        durableCursor.commit(pollResult);

        verify(durableCursor, times(1)).commit(pollResult);
    }

    @Test
    public void positionManager() {
        final PositionManager positionManager = mock(PositionManager.class);
        when(durableCursor.getPositionManager()).thenReturn(positionManager);

        final PositionManager positionManagerTest = durableCursor.getPositionManager();

        Assertions.assertThat(positionManagerTest).isNotNull();
        Assertions.assertThat(positionManagerTest).isEqualTo(positionManager);
    }
}
