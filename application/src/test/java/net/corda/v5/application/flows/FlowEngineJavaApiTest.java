package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlowEngineJavaApiTest {

    private final FlowEngine flowEngine = mock(FlowEngine.class);

    @Test
    public void getFlowId() {
        UUID test = UUID.randomUUID();
        when(flowEngine.getFlowId()).thenReturn(test);

        UUID result = flowEngine.getFlowId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void subFlow() {
        @SuppressWarnings("unchecked")
        Flow<Object> flow = mock(Flow.class);
        doReturn(Object.class).when(flowEngine).subFlow(flow);

        Object result = flowEngine.subFlow(flow);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(Object.class);
        verify(flowEngine, times(1)).subFlow(flow);
    }

    @Test
    public void sleep() {
        Duration duration = Duration.ZERO;
        flowEngine.sleep(duration);
        verify(flowEngine, times(1)).sleep(duration);
    }
}
