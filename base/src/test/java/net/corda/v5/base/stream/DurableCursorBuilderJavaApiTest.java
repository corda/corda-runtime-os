package net.corda.v5.base.stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DurableCursorBuilderJavaApiTest {

    @SuppressWarnings("unchecked")
    private final DurableCursorBuilder<Integer> durableCursorBuilder = mock(DurableCursorBuilder.class);

    @Test
    public void positionManager() {
        final PositionManager positionManager = mock(PositionManager.class);
        when(durableCursorBuilder.getPositionManager()).thenReturn(positionManager);

        final PositionManager positionManagerTest = durableCursorBuilder.getPositionManager();

        Assertions.assertThat(positionManagerTest).isNotNull();
        Assertions.assertThat(positionManagerTest).isEqualTo(positionManager);
    }

    @Test
    public void build() {
        @SuppressWarnings("unchecked")
        final DurableCursor<Integer> durableCursor = mock(DurableCursor.class);
        when(durableCursorBuilder.build()).thenReturn(durableCursor);

        final DurableCursor<Integer> durableCursorTest = durableCursorBuilder.build();

        Assertions.assertThat(durableCursorTest).isNotNull();
        Assertions.assertThat(durableCursorTest).isEqualTo(durableCursor);
    }
}
