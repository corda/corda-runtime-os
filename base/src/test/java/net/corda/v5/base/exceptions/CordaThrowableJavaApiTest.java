package net.corda.v5.base.exceptions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CordaThrowableJavaApiTest {

    private final CordaThrowable cordaThrowable = mock(CordaThrowable.class);

    @Test
    public void getOriginalExceptionClassName() {
        String test = "test";
        when(cordaThrowable.getOriginalExceptionClassName()).thenReturn(test);

        String result = cordaThrowable.getOriginalExceptionClassName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void getOriginalMessage() {
        String test = "test";
        when(cordaThrowable.getOriginalMessage()).thenReturn(test);

        String result = cordaThrowable.getOriginalMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void setMessage() {
        cordaThrowable.setMessage(anyString());
        verify(cordaThrowable, times(1)).setMessage(anyString());
    }

    @Test
    public void setCause() {
        Throwable testThrowable = new Throwable("test");
        cordaThrowable.setCause(testThrowable);
        verify(cordaThrowable, times(1)).setCause(testThrowable);
    }

    @Test
    public void addSuppressed() {
        Throwable testThrowable = new Throwable("test");
        Throwable[] throwableArray = {testThrowable};

        cordaThrowable.addSuppressed(throwableArray);
        verify(cordaThrowable, times(1)).addSuppressed(throwableArray);
    }
}
