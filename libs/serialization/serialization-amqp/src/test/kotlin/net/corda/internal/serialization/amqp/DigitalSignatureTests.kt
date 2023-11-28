package net.corda.internal.serialization.amqp

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.impl.serialization.PublicKeySerializer
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class DigitalSignatureTests {
    private val kpg: KeyPairGenerator =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }

    private val keyEncodingService = CipherSchemeMetadataImpl()
    private val publicKeySerializer = PublicKeySerializer(keyEncodingService)

    @Test
    fun `DigitalSignatureWithKey is serializable`() {
        val publicKey = kpg.generateKeyPair().public
        val digitalSignatureWithKey = DigitalSignatureWithKey(
            publicKey,
            byteArrayOf(0x01, 0x02, 0x03)
        )
        val factory = testDefaultFactory()
        factory.register(publicKeySerializer, factory)
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(digitalSignatureWithKey, factory)
    }

    @Test
    fun `DigitalSignatureWithKeyId is serializable`() {
        val digitalSignatureWithKeyId = DigitalSignatureWithKeyId(
            SecureHashImpl("ALGO", byteArrayOf(0x01, 0x02)),
            byteArrayOf(0x01, 0x02, 0x03)
        )

        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(digitalSignatureWithKeyId)
    }
}
