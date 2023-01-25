package net.corda.httprpc.durablestream.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CursorExceptionJavaApiTest {

    @Test
    public void initialize() {
        final Exception exception = new Exception();
        final CursorException cursorException = new CursorException("msg", null);
        final CursorException cursorException1 = new CursorException("msg", exception);

        Assertions.assertThat(cursorException).isNotNull();
        Assertions.assertThat(cursorException1).isNotNull();
    }
}
