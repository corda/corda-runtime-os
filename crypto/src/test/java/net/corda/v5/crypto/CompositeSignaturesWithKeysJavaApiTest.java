package net.corda.v5.crypto;

import net.corda.v5.crypto.mocks.CryptoTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompositeSignaturesWithKeysJavaApiTest {
    @Test
    @Timeout(5)
    public void emptyConstantTest() {
        assertTrue(CompositeSignaturesWithKeys.EMPTY.getSigs().isEmpty());
        var sig = new DigitalSignature.WithKey(
            CryptoTestUtils.generateKeyPair(CryptoTestUtils.getECDSA_SECP256K1_SPEC()).getPublic(),
            "abc".getBytes()
        );
        assertThrows(UnsupportedOperationException.class, () -> CompositeSignaturesWithKeys.EMPTY.getSigs().add(sig));
    }
}
