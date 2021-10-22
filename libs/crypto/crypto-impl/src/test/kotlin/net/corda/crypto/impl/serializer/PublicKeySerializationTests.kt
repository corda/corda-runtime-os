package net.corda.crypto.impl.serializer

import net.corda.crypto.CryptoLibraryFactory
import net.corda.v5.cipher.suite.KeyEncodingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import java.security.PublicKey
import kotlin.test.assertEquals

class PublicKeySerializationTests {
    @Test
    @Timeout(5)
    fun `Should serialize and then deserialize public key`() {
        val encodedPublicKey = ByteArray(1)
        val publicKey = mock<PublicKey> {
            on { it.encoded }.thenReturn(encodedPublicKey)
        }
        val keyEncodingService = mock<KeyEncodingService> {
            on { it.decodePublicKey(encodedPublicKey) }.thenReturn(publicKey)
            on { it.encodeAsByteArray(publicKey) }.thenReturn(encodedPublicKey)
        }
        val cryptoLibraryFactory = mock<CryptoLibraryFactory> {
            on { it.getKeyEncodingService() }.thenReturn(keyEncodingService)
        }
        val publicKeySerializer = PublicKeySerializer(cryptoLibraryFactory)

        // Convert to proxy object
        val proxy = publicKeySerializer.toProxy(publicKey)

        // Convert back to public key
        val keyAfterConversion = publicKeySerializer.fromProxy(proxy)

        assertEquals(publicKey, keyAfterConversion)
    }
}