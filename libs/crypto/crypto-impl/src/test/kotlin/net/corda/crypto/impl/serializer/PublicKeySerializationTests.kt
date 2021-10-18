package net.corda.crypto.impl.serializer

import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import kotlin.test.assertEquals

class PublicKeySerializationTests {
    @Test
    fun `test custom serializers on public key`() {

        // Generate new key for testing
        val publicKey = KeyPairGenerator.getInstance("RSA").genKeyPair().public

        // Setup PublicKeySerializer with mock CipherSchemeMetadata
        val cipherSchemeMetadata: CipherSchemeMetadata = org.mockito.kotlin.mock<CipherSchemeMetadata>().also {
            whenever(it.decodePublicKey(eq(publicKey.encoded))).thenReturn(publicKey)
        }
        val publicKeySerializer = PublicKeySerializer(cipherSchemeMetadata)

        // Build serialization factory
        val serializerFactory = SerializerFactoryBuilder.build(AllWhitelist)
        serializerFactory.register(publicKeySerializer, true)

        // Run public key through serialization/deserialization and compare
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(publicKey, serializerFactory)
    }
}