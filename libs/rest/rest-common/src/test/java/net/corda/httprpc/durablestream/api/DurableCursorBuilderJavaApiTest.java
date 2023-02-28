package net.corda.rest.durablestream.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DurableCursorBuilderJavaApiTest {

    @SuppressWarnings("unchecked")
    private final DurableCursorBuilder<Integer> durableCursorBuilder = Mockito.mock(DurableCursorBuilder.class);

    @Test
    public void positionManager() {
        final PositionManager positionManager = Mockito.mock(PositionManager.class);
        Mockito.when(durableCursorBuilder.getPositionManager()).thenReturn(positionManager);

        final PositionManager positionManagerTest = durableCursorBuilder.getPositionManager();

        Assertions.assertThat(positionManagerTest).isNotNull();
        Assertions.assertThat(positionManagerTest).isEqualTo(positionManager);
    }

    @Test
    public void build() {
        @SuppressWarnings("unchecked")
        final DurableCursor<Integer> durableCursor = Mockito.mock(DurableCursor.class);
        Mockito.when(durableCursorBuilder.build()).thenReturn(durableCursor);

        final DurableCursor<Integer> durableCursorTest = durableCursorBuilder.build();

        Assertions.assertThat(durableCursorTest).isNotNull();
        Assertions.assertThat(durableCursorTest).isEqualTo(durableCursor);
    }
}
