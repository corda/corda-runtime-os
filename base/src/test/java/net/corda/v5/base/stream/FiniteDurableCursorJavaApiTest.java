package net.corda.v5.base.stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FiniteDurableCursorJavaApiTest {

    @Test
    @SuppressWarnings("unchecked")
    public void take() {
        final FiniteDurableCursor<Integer> finiteDurableCursor = mock(FiniteDurableCursor.class);
        final Cursor.PollResult<Integer> pollResult = mock(Cursor.PollResult.class);
        when(finiteDurableCursor.take(5)).thenReturn(pollResult);

        final Cursor.PollResult<Integer> pollResultTest = finiteDurableCursor.take(5);

        Assertions.assertThat(pollResultTest).isNotNull();
        Assertions.assertThat(pollResultTest).isEqualTo(pollResult);
    }
}
