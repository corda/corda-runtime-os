package net.corda.v5.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CordaOIDJavaApiTest {
    @Test
    public void constantTests() {
        assertNotNull(CordaOID.OID_R3_ROOT);
        assertNotNull(CordaOID.OID_CORDA_PLATFORM);
        assertNotNull(CordaOID.OID_X509_EXTENSION_CORDA_ROLE);
        assertNotNull(CordaOID.OID_ALIAS_PRIVATE_KEY);
        assertNotNull(CordaOID.OID_COMPOSITE_KEY);
        assertNotNull(CordaOID.OID_COMPOSITE_SIGNATURE);
        assertNotNull(CordaOID.OID_ALIAS_PRIVATE_KEY_IDENTIFIER);
        assertNotNull(CordaOID.OID_COMPOSITE_KEY_IDENTIFIER);
        assertNotNull(CordaOID.OID_COMPOSITE_SIGNATURE_IDENTIFIER);
    }
}
