package net.corda.httprpc.durablestream.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PositionedValueJavaApiTest {
    @SuppressWarnings("unchecked")
    private final Cursor.PollResult.PositionedValue<Integer> positionedValue = Mockito.mock(Cursor.PollResult.PositionedValue.class);

    @Test
    public void value() {
        Mockito.when(positionedValue.getValue()).thenReturn(5);

        final Integer integer = positionedValue.getValue();

        Assertions.assertThat(integer).isNotNull();
        Assertions.assertThat(integer).isEqualTo(5);
    }

    @Test
    public void position() {
        Mockito.when(positionedValue.getPosition()).thenReturn(5L);

        final Long aLong = positionedValue.getPosition();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }
}
