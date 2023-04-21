package net.corda.v5.base.exceptions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CordaRuntimeExceptionJavaApiTest {

    private final Throwable throwable = new Throwable();
    private final CordaRuntimeException cordaRuntimeException = new CordaRuntimeException(
            "testOriginalExceptionClassName",
            "testMessage",
            throwable);

    @Test
    public void getMessage() {
        String result = cordaRuntimeException.getMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testOriginalExceptionClassName: testMessage");
    }

    @Test
    public void getCause() {
        Throwable result = cordaRuntimeException.getCause();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(throwable);
    }

    @Test
    public void setMessage() {
        cordaRuntimeException.setMessage("newTestMessage");
        String result = cordaRuntimeException.getMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testOriginalExceptionClassName: newTestMessage");
        Assertions.assertThat(result).isNotEqualTo("testOriginalExceptionClassName: testMessage");
    }

    @Test
    public void setCause() {
        Throwable newThrowable = new Throwable();
        cordaRuntimeException.setCause(newThrowable);
        Throwable result = cordaRuntimeException.getCause();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(newThrowable);
        Assertions.assertThat(result).isNotEqualTo(throwable);
    }

    @Test
    public void addSuppressed() {
        Throwable[] throwableArray = {throwable};
        cordaRuntimeException.addSuppressed(throwableArray);
    }

    @Test
    public void getOriginalMessage() {
        String result = cordaRuntimeException.getOriginalMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testMessage");
    }

    @Test
    public void getOriginalExceptionClassName() {
        String result = cordaRuntimeException.getOriginalExceptionClassName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testOriginalExceptionClassName");
    }
}
