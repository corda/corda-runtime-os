package net.corda.v5.crypto;

import net.corda.v5.crypto.mocks.CryptoTestUtils;
import org.bouncycastle.asn1.ASN1Primitive;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeKeyJavaApiTest {
    @Test
    public void keyAlgorithmConstantTest() {
        assertEquals("COMPOSITE", CompositeKey.KEY_ALGORITHM);
    }

    @Test
    public void getInstanceTest() throws IOException {
        var alicePublicKey = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        var bobPublicKey = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256R1_SPEC()).getPublic();
        var charliePublicKey = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getEDDSA_ED25519_SPEC()).getPublic();
        var aliceAndBob = new CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build();
        var aliceAndBobOrCharlie = new CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build();
        var encoded = aliceAndBobOrCharlie.getEncoded();
        var decoded = CompositeKey.getInstance(ASN1Primitive.fromByteArray(encoded), CryptoTestUtils::decodePublicKey);
        assertEquals(decoded, aliceAndBobOrCharlie);
    }
}