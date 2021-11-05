package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class UnexpectedFlowEndExceptionJavaApiTest {

    private final String message = "message";
    private final Throwable throwable = mock(Throwable.class);

    @Test
    public void initialize() {
        final UnexpectedFlowEndException unexpectedFlowEndException = new UnexpectedFlowEndException(message);
        final UnexpectedFlowEndException unexpectedFlowEndException1 = new UnexpectedFlowEndException(message, throwable);
        final UnexpectedFlowEndException unexpectedFlowEndException2 = new UnexpectedFlowEndException(message, throwable, 1l);
    }

    @Test
    public void unexpectedFlowEndExceptionWithErrorId() {
        final UnexpectedFlowEndException unexpectedFlowEndException = new UnexpectedFlowEndException(message);
        final UnexpectedFlowEndException unexpectedFlowEndException1 = new UnexpectedFlowEndException(message, throwable, 1L);

        Assertions.assertThat(unexpectedFlowEndException.getErrorId()).isNull();
        Assertions.assertThat(unexpectedFlowEndException1.getErrorId()).isNotNull();
    }
}
