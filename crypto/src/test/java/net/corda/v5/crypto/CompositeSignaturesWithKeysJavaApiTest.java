package net.corda.v5.crypto;

import net.corda.v5.crypto.mocks.CryptoTestUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompositeSignaturesWithKeysJavaApiTest {
    @Test
    public void emptyConstantTest() {
        assertTrue(CompositeSignaturesWithKeys.EMPTY.getSigs().isEmpty());
        var sig = new DigitalSignature.WithKey(
            CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic(),
            "abc".getBytes(),
            new HashMap<>()
        );
        assertThrows(UnsupportedOperationException.class, () -> CompositeSignaturesWithKeys.EMPTY.getSigs().add(sig));
    }
}
