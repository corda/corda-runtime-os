package net.corda.crypto.cipher.suite.schemes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeySchemeTemplatesJavaApiTest {
    @Test
    public void constantsTests() {
        assertNotNull(KeySchemeTemplates.ID_CURVE_25519PH);
        assertNotNull(KeySchemeTemplates.ID_CURVE_X25519);
        assertNotNull(KeySchemeTemplates.SHA512_256);
        assertNotNull(KeySchemeTemplates.RSA_TEMPLATE);
        assertNotNull(KeySchemeTemplates.ECDSA_SECP256K1_TEMPLATE);
        assertNotNull(KeySchemeTemplates.ECDSA_SECP256R1_TEMPLATE);
        assertNotNull(KeySchemeTemplates.EDDSA_ED25519_TEMPLATE);
        assertNotNull(KeySchemeTemplates.X25519_TEMPLATE);
        assertNotNull(KeySchemeTemplates.SPHINCS256_TEMPLATE);
        assertNotNull(KeySchemeTemplates.SM2_TEMPLATE);
        assertNotNull(KeySchemeTemplates.GOST3410_GOST3411_TEMPLATE);
        assertEquals(8, KeySchemeTemplates.all.size());
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.RSA_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.ECDSA_SECP256K1_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.ECDSA_SECP256R1_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.EDDSA_ED25519_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.X25519_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.SPHINCS256_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.SM2_TEMPLATE));
        assertTrue(KeySchemeTemplates.all.contains(KeySchemeTemplates.GOST3410_GOST3411_TEMPLATE));
    }
}
