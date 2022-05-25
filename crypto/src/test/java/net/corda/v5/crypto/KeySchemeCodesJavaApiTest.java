package net.corda.v5.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KeySchemeCodesJavaApiTest {
    @Test
    public void constantsTests() {
        assertNotNull(KeySchemeCodes.RSA_CODE_NAME);
        assertNotNull(KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME);
        assertNotNull(KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME);
        assertNotNull(KeySchemeCodes.EDDSA_ED25519_CODE_NAME);
        assertNotNull(KeySchemeCodes.SM2_CODE_NAME);
        assertNotNull(KeySchemeCodes.GOST3410_GOST3411_CODE_NAME);
        assertNotNull(KeySchemeCodes.SPHINCS256_CODE_NAME);
        assertNotNull(KeySchemeCodes.COMPOSITE_KEY_CODE_NAME);
    }
}
