package net.corda.v5.crypto;

import net.corda.v5.base.util.EncodingUtils;
import net.corda.v5.crypto.mocks.CryptoTestUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoUtilsJavaApiTest {
    @Test
    public void ShouldComputeCorrectlySHA25forGivenByteArray() {
        var hash = CryptoUtils.sha256Bytes("42".getBytes(StandardCharsets.UTF_8));
        var expected = new byte[]{
            115, 71, 92, -76, 10, 86, -114, -115, -88, -96, 69, -50, -47, 16, 19, 126, 21, -97, -119, 10, -60, -38, -120,
            59, 107, 23, -36, 101, 27, 58, -128, 73
        };
        assertArrayEquals(expected, hash);
    }

    @Test
    public void ShouldComputeCorrectlySHA25forGivenPublicKey() throws Exception {
        var key = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        var hash = CryptoUtils.sha256Bytes(key);
        var expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.getName()).digest(key.getEncoded());
        assertArrayEquals(expected, hash);
    }

    @Test
    public void toStringShortShouldReturnBase58WithDLPrefixOfSHA256forGivenPublicKey() throws Exception {
        var key = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        var str = CryptoUtils.toStringShort(key);
        var expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.getName()).digest(key.getEncoded());
        var expectedStr = EncodingUtils.toBase58(expected);
        assertEquals("DL" + expectedStr, str);
    }

    @Test
    public void keysShouldReturnCollectionConsistingOfItselfForGivenPublicKey() {
        var key = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        var result = CryptoUtils.getKeys(key);
        assertEquals(1, result.size());
        assertEquals(key, result.toArray()[0]);
    }

    @Test
    public void sFulfilledByOverloadWithSingleKeyShouldReturnTrueIfKeysAreMatchingForGivenPublicKey() {
        var key = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        assertTrue(CryptoUtils.isFulfilledBy(key, key));
    }

    @Test
    public void isFulfilledByOverloadWithCollectionShouldReturnTrueIfKeysMatchingAtLeastOneGivenPublicKey() {
        var key = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        var list = new ArrayList<PublicKey>();
        list.add(CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic());
        list.add(key);
        assertTrue(CryptoUtils.isFulfilledBy(key, list));
    }

    @Test
    public void containsAnyShouldReturnTrueIfKeyIsInCollection() {
        var key = CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic();
        var list = new ArrayList<PublicKey>();
        list.add(CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic());
        list.add(key);
        assertTrue(CryptoUtils.containsAny(key, list));
    }

    @Test
    public void byKeysShouldReturnSetOfAllPublicKeysOfDigitalSignatureWithKeyCollection() {
        var signature1 = new DigitalSignature.WithKey(
            CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic(),
            "abc".getBytes(),
            new HashMap<>()
        );
        var signature2 = new DigitalSignature.WithKey(
            CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic(),
            "abc".getBytes(),
            new HashMap<>()
        );
        var signatures = new ArrayList<DigitalSignature.WithKey>();
        signatures.add(signature1);
        signatures.add(signature2);
        var result = CryptoUtils.byKeys(signatures);
        assertEquals(2, result.size());
        assertTrue(result.contains(signature1.getBy()));
        assertTrue(result.contains(signature2.getBy()));
    }

    @Test
    public void publicConstsShouldLookNiceInJava() {
        assertEquals(20, CryptoUtils.KEY_LOOKUP_INPUT_ITEMS_LIMIT);
        assertEquals(10, CryptoUtils.COMPOSITE_KEY_CHILDREN_LIMIT);
    }
}
