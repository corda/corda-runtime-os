package net.corda.v5.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SignatureSpecJavaApiTest {
    @Test
    public void constantsTests() {
        assertNotNull(SignatureSpec.RSA_SHA256);
        assertNotNull(SignatureSpec.RSA_SHA384);
        assertNotNull(SignatureSpec.RSA_SHA512);
        assertNotNull(SignatureSpec.RSASSA_PSS_SHA256);
        assertNotNull(SignatureSpec.RSASSA_PSS_SHA384);
        assertNotNull(SignatureSpec.RSASSA_PSS_SHA512);
        assertNotNull(SignatureSpec.RSA_SHA256_WITH_MGF1);
        assertNotNull(SignatureSpec.RSA_SHA384_WITH_MGF1);
        assertNotNull(SignatureSpec.RSA_SHA512_WITH_MGF1);
        assertNotNull(SignatureSpec.SM2_SM3);
        assertNotNull(SignatureSpec.SM2_SHA256);
        assertNotNull(SignatureSpec.ECDSA_SHA256);
        assertNotNull(SignatureSpec.ECDSA_SHA384);
        assertNotNull(SignatureSpec.ECDSA_SHA512);
        assertNotNull(SignatureSpec.EDDSA_ED25519);
        assertNotNull(SignatureSpec.GOST3410_GOST3411);
        assertNotNull(SignatureSpec.SPHINCS256_SHA512);
    }
}
