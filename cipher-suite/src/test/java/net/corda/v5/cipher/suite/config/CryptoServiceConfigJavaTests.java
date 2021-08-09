package net.corda.v5.cipher.suite.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CryptoServiceConfigJavaTests {
    @Test
    public void defaultServiceNameFromJava() {
        Assertions.assertEquals("default", CryptoServiceConfig.DEFAULT_SERVICE_NAME);
    }
}
