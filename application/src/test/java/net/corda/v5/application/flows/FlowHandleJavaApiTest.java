package net.corda.v5.application.flows;

import net.corda.v5.application.messaging.FlowHandle;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowHandleJavaApiTest {

    @SuppressWarnings("unchecked")
    private final FlowHandle<Object> flowHandle = mock(FlowHandle.class);

    @Test
    public void id() {
        final FlowId flowId = FlowId.Companion.createRandom();
        when(flowHandle.getId()).thenReturn(flowId);

        final FlowId flowIdTest = flowHandle.getId();

        Assertions.assertThat(flowIdTest).isNotNull();
        Assertions.assertThat(flowIdTest).isEqualTo(flowId);
    }

    @Test
    public void returnValue() {
        @SuppressWarnings("unchecked")
        final CompletableFuture<Object> future = mock(CompletableFuture.class);
        when(flowHandle.getReturnValue()).thenReturn(future);

        final CompletableFuture<Object> futureTest = flowHandle.getReturnValue();

        Assertions.assertThat(futureTest).isNotNull();
        Assertions.assertThat(futureTest).isEqualTo(future);
    }
}
