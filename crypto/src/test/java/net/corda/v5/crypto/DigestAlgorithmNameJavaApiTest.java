package net.corda.v5.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DigestAlgorithmNameJavaApiTest {
    @Test
    public void constantsTests() {
        assertEquals("SHA-256", DigestAlgorithmName.SHA2_256.getName());
        assertEquals("SHA-256D", DigestAlgorithmName.SHA2_256D.getName());
        assertEquals("SHA-384", DigestAlgorithmName.SHA2_384.getName());
        assertEquals("SHA-512", DigestAlgorithmName.SHA2_512.getName());
    }
}
