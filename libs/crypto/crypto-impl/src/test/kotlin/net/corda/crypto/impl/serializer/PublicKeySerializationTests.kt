package net.corda.crypto.impl.serializer

import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import kotlin.test.assertEquals

class PublicKeySerializationTests {
    @Test
    fun `test custom serializers on public key`() {

        // Generate new key for testing
        val publicKey = KeyPairGenerator.getInstance("RSA").genKeyPair().public

        val publicKeySerializer = PublicKeySerializer()

        // Convert to proxy object
        val proxy = publicKeySerializer.toProxy(publicKey)

        // Convert back to public key
        val keyAfterConversion = publicKeySerializer.fromProxy(proxy)

        assertEquals(publicKey, keyAfterConversion)
    }
}