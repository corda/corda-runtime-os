package net.corda.v5.cipher.suite.schemes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SchemeTemplatesJavaApiTest {
    @Test
    @Timeout(5)
    public void constantsTests() {
        assertNotNull(SchemeTemplates.RSA_CODE_NAME);
        assertNotNull(SchemeTemplates.ECDSA_SECP256K1_CODE_NAME);
        assertNotNull(SchemeTemplates.ECDSA_SECP256R1_CODE_NAME);
        assertNotNull(SchemeTemplates.EDDSA_ED25519_CODE_NAME);
        assertNotNull(SchemeTemplates.SM2_CODE_NAME);
        assertNotNull(SchemeTemplates.GOST3410_GOST3411_CODE_NAME);
        assertNotNull(SchemeTemplates.SPHINCS256_CODE_NAME);
        assertNotNull(SchemeTemplates.COMPOSITE_KEY_CODE_NAME);
        assertNotNull(SchemeTemplates.ID_CURVE_25519PH);
        assertNotNull(SchemeTemplates.SHA512_256);
        assertNotNull(SchemeTemplates.RSA_SHA256_TEMPLATE);
        assertNotNull(SchemeTemplates.ECDSA_SECP256K1_SHA256_TEMPLATE);
        assertNotNull(SchemeTemplates.ECDSA_SECP256R1_SHA256_TEMPLATE);
        assertNotNull(SchemeTemplates.EDDSA_ED25519_NONE_TEMPLATE);
        assertNotNull(SchemeTemplates.SPHINCS256_SHA512_TEMPLATE);
        assertNotNull(SchemeTemplates.SM2_SM3_TEMPLATE);
        assertNotNull(SchemeTemplates.GOST3410_GOST3411_TEMPLATE);
        assertNotNull(SchemeTemplates.COMPOSITE_KEY_TEMPLATE);
    }
}
