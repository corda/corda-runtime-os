package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class FlowExceptionJavaApiTest {

    private final String message = "message";
    private final Throwable throwable = mock(Throwable.class);

    @Test
    public void initialize() {
        final FlowException flowException = new FlowException();
        final FlowException flowException1 = new FlowException(message);
        final FlowException flowException2 = new FlowException(throwable);
        final FlowException flowException3 = new FlowException(message, throwable);
        final FlowException flowException4 = new FlowException(message, throwable, 1l);
    }

    @Test
    public void flowExceptionWithErrorId() {
        final FlowException flowException = new FlowException();
        final FlowException flowExceptionTest = new FlowException(message, throwable, 1L);

        Assertions.assertThat(flowException.getErrorId()).isNull();
        Assertions.assertThat(flowExceptionTest.getErrorId()).isNotNull();
    }
}
