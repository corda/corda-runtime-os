package net.corda.crypto.impl.serialization

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.PublicKey
import kotlin.test.assertEquals

class PublicKeySerializationTests {
    @Test
    fun `Should serialize and then deserialize public key`() {
        val encodedPublicKey = ByteArray(1)
        val publicKey = mock<PublicKey> {
            on { it.encoded }.thenReturn(encodedPublicKey)
        }
        val keyEncodingService = mock<CipherSchemeMetadata> {
            on { it.decodePublicKey(encodedPublicKey) }.thenReturn(publicKey)
            on { it.encodeAsByteArray(publicKey) }.thenReturn(encodedPublicKey)
        }
        val publicKeySerializer = PublicKeySerializer(keyEncodingService)

        argumentCaptor<ByteArray> {
            val writeOps = mock<WriteObject>()

            // Write to AMQP output
            publicKeySerializer.writeObject(publicKey, writeOps, mock())
            verify(writeOps).putAsBytes(capture())

            val readOps = mock<ReadObject> {
                on(ReadObject::getAsBytes).thenReturn(firstValue)
            }

            // Convert back to public key
            val keyAfterConversion = publicKeySerializer.readObject(readOps, mock())

            assertEquals(publicKey, keyAfterConversion)
        }
    }
}
