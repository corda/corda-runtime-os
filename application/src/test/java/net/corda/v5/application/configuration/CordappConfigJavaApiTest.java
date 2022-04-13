package net.corda.v5.application.configuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CordappConfigJavaApiTest {

    private final CordappConfig cordappConfig = mock(CordappConfig.class);

    @Test
    public void exists() {
        when(cordappConfig.exists("path")).thenReturn(true);

        boolean result = cordappConfig.exists("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);
    }

    @Test
    public void get() {
        when(cordappConfig.get("path")).thenReturn(1);

        Object result = cordappConfig.get("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1);
    }

    @Test
    public void getInt() {
        when(cordappConfig.getInt("path")).thenReturn(1);

        int result = cordappConfig.getInt("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1);
    }

    @Test
    public void getLong() {
        when(cordappConfig.getLong("path")).thenReturn(1L);

        long result = cordappConfig.getLong("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1L);
    }

    @Test
    public void getFloat() {
        when(cordappConfig.getFloat("path")).thenReturn(1.0F);

        float result = cordappConfig.getFloat("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1.0F);
    }

    @Test
    public void getDouble() {
        when(cordappConfig.getDouble("path")).thenReturn(1.0);

        double result = cordappConfig.getDouble("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1.0);
    }

    @Test
    public void getNumber() {
        when(cordappConfig.getNumber("path")).thenReturn(1.0);

        Number result = cordappConfig.getNumber("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1.0);
    }

    @Test
    public void getString() {
        when(cordappConfig.getString("path")).thenReturn("result");

        String result = cordappConfig.getString("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("result");
    }

    @Test
    public void getBoolean() {
        when(cordappConfig.getBoolean("path")).thenReturn(true);

        boolean result = cordappConfig.getBoolean("path");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);
    }
}
