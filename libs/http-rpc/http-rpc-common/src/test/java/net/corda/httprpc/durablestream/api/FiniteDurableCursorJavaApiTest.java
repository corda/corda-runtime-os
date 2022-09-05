package net.corda.httprpc.durablestream.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FiniteDurableCursorJavaApiTest {

    @Test
    @SuppressWarnings("unchecked")
    public void take() {
        final FiniteDurableCursor<Integer> finiteDurableCursor = Mockito.mock(FiniteDurableCursor.class);
        final Cursor.PollResult<Integer> pollResult = Mockito.mock(Cursor.PollResult.class);
        Mockito.when(finiteDurableCursor.take(5)).thenReturn(pollResult);

        final Cursor.PollResult<Integer> pollResultTest = finiteDurableCursor.take(5);

        Assertions.assertThat(pollResultTest).isNotNull();
        Assertions.assertThat(pollResultTest).isEqualTo(pollResult);
    }
}
