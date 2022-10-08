package net.corda.crypto.impl;

import net.corda.v5.crypto.CompositeKey;
import org.bouncycastle.asn1.ASN1Primitive;
import org.junit.jupiter.api.Test;
import net.corda.crypto.impl.CryptoTestUtilsKt
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeKeyJavaApiTests {
    @Test
    public void keyAlgorithmConstantTest() {
        assertEquals("COMPOSITE", CompositeKeyImpl.KEY_ALGORITHM);
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