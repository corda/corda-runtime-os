package net.corda.v5.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SignatureSpecsJavaApiTest {
    @Test
    public void constantsTests() {
        assertNotNull(SignatureSpecs.NaSignatureSpec);
        assertNotNull(SignatureSpecs.RSA_SHA256_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSA_SHA384_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSA_SHA512_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSASSA_PSS_SHA256_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSASSA_PSS_SHA384_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSASSA_PSS_SHA512_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSA_SHA256_WITH_MGF1_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSA_SHA384_WITH_MGF1_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.RSA_SHA512_WITH_MGF1_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.SM2_SM3_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.SM2_SHA256_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.ECDSA_SHA256_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.ECDSA_SHA384_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.ECDSA_SHA512_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.EDDSA_ED25519_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.GOST3410_GOST3411_SIGNATURE_SPEC);
        assertNotNull(SignatureSpecs.SPHINCS256_SHA512_SIGNATURE_SPEC);
    }
}
