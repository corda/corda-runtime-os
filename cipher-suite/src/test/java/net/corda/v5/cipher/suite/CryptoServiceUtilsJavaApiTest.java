package net.corda.v5.cipher.suite;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CryptoServiceUtilsJavaApiTest {
    @Test
    public void shouldCalculateHSMAlias() {
        var secureRandom = new SecureRandom();
        var tenant = UUID.randomUUID().toString();
        var alias = UUID.randomUUID().toString();
        var secret = new byte[32];
        secureRandom.nextBytes(secret);
        var a1 = CryptoServiceUtils.computeHSMAlias(tenant, alias, secret);
        assertNotNull(a1);
    }
}
