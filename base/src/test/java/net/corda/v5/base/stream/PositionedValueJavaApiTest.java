package net.corda.v5.base.stream;

import kotlin.Suppress;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PositionedValueJavaApiTest {
    @SuppressWarnings("unchecked")
    private final Cursor.PollResult.PositionedValue<Integer> positionedValue = mock(Cursor.PollResult.PositionedValue.class);

    @Test
    public void value() {
        when(positionedValue.getValue()).thenReturn(5);

        final Integer integer = positionedValue.getValue();

        Assertions.assertThat(integer).isNotNull();
        Assertions.assertThat(integer).isEqualTo(5);
    }

    @Test
    public void position() {
        when(positionedValue.getPosition()).thenReturn(5L);

        final Long aLong = positionedValue.getPosition();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }
}
