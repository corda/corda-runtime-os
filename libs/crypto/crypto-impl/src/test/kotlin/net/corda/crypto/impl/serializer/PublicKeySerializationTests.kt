package net.corda.crypto.impl.serializer

import net.corda.v5.cipher.suite.KeyEncodingService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import kotlin.test.assertEquals

class PublicKeySerializationTests {

    @Test
    fun `test custom serializers on public key`() {

        // Generate new key for testing
        val publicKey: PublicKey = KeyPairGenerator.getInstance(algorithm).genKeyPair().public

        val publicKeySerializer = PublicKeySerializer(mock {
            on { it.getKeyEncodingService() }.doReturn(MockKeyEncodingService())
        })

        // Convert to proxy object
        val proxy = publicKeySerializer.toProxy(publicKey)

        // Convert back to public key
        val keyAfterConversion = publicKeySerializer.fromProxy(proxy)

        assertEquals(publicKey, keyAfterConversion)
    }

    private class MockKeyEncodingService : KeyEncodingService {
        override fun decodePublicKey(encodedKey: ByteArray): PublicKey =
            KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(encodedKey))

        override fun decodePublicKey(encodedKey: String): PublicKey = throw NotImplementedError()
        override fun encodeAsString(publicKey: PublicKey): String = throw NotImplementedError()
        override fun toSupportedPrivateKey(key: PrivateKey): PrivateKey = throw NotImplementedError()
        override fun toSupportedPublicKey(key: PublicKey): PublicKey = throw NotImplementedError()
    }

    companion object {
        private const val algorithm = "RSA"
    }
}