package net.corda.crypto.impl.serializer

import net.corda.crypto.CryptoLibraryFactory
import net.corda.v5.cipher.suite.KeyEncodingService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.PublicKey
import kotlin.test.assertEquals

class PublicKeySerializationTests {

    @Test
    fun `test custom serializers on public key`() {
        val algorithm = "RSA"

        // Generate new key for testing
        val publicKey: PublicKey = KeyPairGenerator.getInstance(algorithm).genKeyPair().public

        val keyEncodingService = mock<KeyEncodingService> {
            on { it.decodePublicKey(publicKey.encoded) }.thenReturn(publicKey)
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