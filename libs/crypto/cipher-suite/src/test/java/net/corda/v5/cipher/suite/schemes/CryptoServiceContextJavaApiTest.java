package net.corda.v5.cipher.suite.schemes;

import net.corda.v5.cipher.suite.CryptoServiceContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CryptoServiceContextJavaApiTest {
    @Test
    public void constantsTest() {
        assertNotNull(CryptoServiceContext.CRYPTO_CATEGORY);
        assertNotNull(CryptoServiceContext.CRYPTO_TENANT_ID);
        assertNotNull(CryptoServiceContext.CRYPTO_KEY_TYPE);
        assertNotNull(CryptoServiceContext.CRYPTO_KEY_TYPE_WRAPPING);
        assertNotNull(CryptoServiceContext.CRYPTO_KEY_TYPE_KEYPAIR);
    }
}
