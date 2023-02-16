package net.corda.httprpc.durablestream.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;


public class PollResultJavaApiTest {

    @SuppressWarnings("unchecked")
    private final Cursor.PollResult<Integer> pollResult = Mockito.mock(Cursor.PollResult.class);

    @Test
    public void positionedValues() {
        @SuppressWarnings("unchecked")
        final List<Cursor.PollResult.PositionedValue<Integer>> positionedValues = Mockito.mock(List.class);
        Mockito.when(pollResult.getPositionedValues()).thenReturn(positionedValues);

        final List<Cursor.PollResult.PositionedValue<Integer>> positionedValuesTest = pollResult.getPositionedValues();

        Assertions.assertThat(positionedValuesTest).isNotNull();
        Assertions.assertThat(positionedValuesTest).isEqualTo(positionedValues);
    }

    @Test
    public void values() {
        final List<Integer> integers = List.of(5, 6);
        Mockito.when(pollResult.getValues()).thenReturn(integers);

        final List<Integer> integersTest = pollResult.getValues();

        Assertions.assertThat(integersTest).isNotNull();
        Assertions.assertThat(integersTest).isEqualTo(integers);
    }

    @Test
    public void firstPosition() {
        Mockito.when(pollResult.getFirstPosition()).thenReturn(5L);

        final Long aLong = pollResult.getFirstPosition();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }

    @Test
    public void lastPosition() {
        Mockito.when(pollResult.getLastPosition()).thenReturn(5L);

        final Long aLong = pollResult.getLastPosition();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }

    @Test
    public void remainingElementsCountEstimate() {
        Mockito.when(pollResult.getRemainingElementsCountEstimate()).thenReturn(5L);

        final Long aLong = pollResult.getRemainingElementsCountEstimate();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(5L);
    }

    @Test
    public void remainingElementsCountEstimate_without_shouldReturnNull() {
        Mockito.when(pollResult.getRemainingElementsCountEstimate()).thenReturn(null);

        final Long aLong = pollResult.getRemainingElementsCountEstimate();

        Assertions.assertThat(aLong).isNull();
    }

    @Test
    public void isEmpty() {
        Mockito.when(pollResult.isEmpty()).thenReturn(false);

        final Boolean isEmpty = pollResult.isEmpty();

        Assertions.assertThat(isEmpty).isNotNull();
        Assertions.assertThat(isEmpty).isFalse();
    }

    @Test
    public void isLastResult() {
        Mockito.when(pollResult.isLastResult()).thenReturn(false);

        final Boolean isLastResult = pollResult.isLastResult();

        Assertions.assertThat(isLastResult).isNotNull();
        Assertions.assertThat(isLastResult).isFalse();
    }
}
