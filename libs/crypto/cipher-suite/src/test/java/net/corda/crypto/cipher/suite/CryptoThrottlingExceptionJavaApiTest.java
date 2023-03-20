package net.corda.crypto.cipher.suite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CryptoThrottlingExceptionJavaApiTest {
    @Test
    public void canCreateExceptions() {
        assertNotNull(CryptoThrottlingException.createExponential("error"));
        assertNotNull(CryptoThrottlingException.createExponential("error", new RuntimeException()));
        assertNotNull(CryptoThrottlingException.createExponential("error", 6, 1000));
        assertNotNull(CryptoThrottlingException.createExponential(
                "error",
                new RuntimeException(),
                6,
                1000)
        );
    }
}
