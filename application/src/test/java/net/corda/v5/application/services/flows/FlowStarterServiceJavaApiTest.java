package net.corda.v5.application.services.flows;

import net.corda.v5.application.flows.Flow;
import net.corda.v5.application.messaging.FlowHandle;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowStarterServiceJavaApiTest {

    private final FlowStarterService flowStarterService = mock(FlowStarterService.class);
    @SuppressWarnings("unchecked")
    private final FlowHandle<? super Object> flowHandle = mock(FlowHandle.class);
    @SuppressWarnings("unchecked")
    private final Flow<Object> flow = mock(Flow.class);

    @Test
    public void startFlow() {
        when(flowStarterService.startFlow(flow)).thenReturn(flowHandle);

        FlowHandle<?> result = flowStarterService.startFlow(flow);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(flowHandle);
    }
}
