package net.corda.v5.application.flows.flowservices;

import kotlin.jvm.functions.Function0;
import net.corda.v5.application.flows.Flow;
import net.corda.v5.application.flows.FlowExternalOperation;
import net.corda.v5.application.flows.FlowId;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlowEngineJavaApiTest {

    private final FlowEngine flowEngine = mock(FlowEngine.class);

    @Test
    public void getFlowId() {
        FlowId test = new FlowId(UUID.randomUUID());
        when(flowEngine.getFlowId()).thenReturn(test);

        FlowId result = flowEngine.getFlowId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void isKilled() {
        when(flowEngine.isKilled()).thenReturn(true);

        Boolean result = flowEngine.isKilled();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);
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
    public void checkFlowIsNotKilled() {
        flowEngine.checkFlowIsNotKilled();
        verify(flowEngine, times(1)).checkFlowIsNotKilled();
    }

    @Test
    public void checkFlowIsNotKilledLazyMessage() {
        Function0<Integer> lambda = () -> 1;
        flowEngine.checkFlowIsNotKilled(lambda);
        verify(flowEngine, times(1)).checkFlowIsNotKilled(lambda);
    }

    @Test
    public void checkFlowIsNotKilledLazyMessageWithLambda() {
        flowEngine.checkFlowIsNotKilled(() -> 1);
        verify(flowEngine, times(1)).checkFlowIsNotKilled(any());
    }

    @Test
    public void sleep() {
        Duration duration = Duration.ZERO;
        flowEngine.sleep(duration);
        verify(flowEngine, times(1)).sleep(duration);
    }

    @Test
    public void await() {
        MyFlowExternalOperation flowExternalOperation = new MyFlowExternalOperation("testProperty");
        doReturn("testProperty").when(flowEngine).await(flowExternalOperation);

        String result = flowEngine.await(flowExternalOperation);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testProperty");
        verify(flowEngine, times(1)).await(flowExternalOperation);
    }

    static class MyFlowExternalOperation implements FlowExternalOperation<String> {

        private final String property;

        MyFlowExternalOperation(String property) {
            this.property = property;
        }

        @Override
        public String execute(@NotNull String deduplicationId) {
            return property;
        }
    }
}
