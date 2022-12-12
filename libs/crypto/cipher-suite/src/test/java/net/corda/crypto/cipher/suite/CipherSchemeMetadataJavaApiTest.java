package net.corda.crypto.cipher.suite;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CipherSchemeMetadataJavaApiTest {
    @Test
    public void bannedDigestFromJava() {
        assertEquals(Set.of("MD5", "MD2", "SHA-1", "MD4", "HARAKA-256", "HARAKA-512"), CipherSchemeMetadata.BANNED_DIGESTS);
    }

    @Test
    public void bannedDigestCannotBeModified() {
        assertThrows(UnsupportedOperationException.class, CipherSchemeMetadata.BANNED_DIGESTS::clear);
    }
}
