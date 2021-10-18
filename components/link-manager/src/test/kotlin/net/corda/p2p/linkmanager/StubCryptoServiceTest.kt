package net.corda.p2p.linkmanager

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ECDSA_SIGNATURE_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RSA_SIGNATURE_ALGO
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature

class StubCryptoServiceTest {

    private var clientProcessor: CompactedProcessor<String, KeyPairEntry>? = null

    private val subscriptionFactory = mock<SubscriptionFactory>() {
        on { createCompactedSubscription(
            any(),
            any<CompactedProcessor<String, KeyPairEntry>>(),
            any(),
            anyOrNull()
        ) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            clientProcessor = invocation.arguments[1] as CompactedProcessor<String, KeyPairEntry>
            mock()
        }
    }

    private val cryptoService = StubCryptoService(subscriptionFactory).apply {
        start()
    }

    private val rsaKeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    private val ecdsaKeyPairGenerator = KeyPairGenerator.getInstance("EC")

    private val rsaSignature = Signature.getInstance(RSA_SIGNATURE_ALGO)
    private val ecdsaSignature = Signature.getInstance(ECDSA_SIGNATURE_ALGO)

    private val firstKeyPair = rsaKeyPairGenerator.genKeyPair()
    private val secondKeyPair = ecdsaKeyPairGenerator.genKeyPair()

    @Test
    fun `crypto service can sign payloads successfully`() {
        val snapshot = mapOf(
            "key-1" to KeyPairEntry(
                KeyAlgorithm.RSA,
                ByteBuffer.wrap(firstKeyPair.public.encoded),
                ByteBuffer.wrap(firstKeyPair.private.encoded)
            ),
            "key-2" to KeyPairEntry(
                KeyAlgorithm.ECDSA,
                ByteBuffer.wrap(secondKeyPair.public.encoded),
                ByteBuffer.wrap(secondKeyPair.private.encoded)
            )
        )
        clientProcessor!!.onSnapshot(snapshot)
        val payload = "some-payload".toByteArray()

        var signedData = cryptoService.signData(firstKeyPair.public, payload)
        rsaSignature.initVerify(firstKeyPair.public)
        rsaSignature.update(payload)
        assertTrue(rsaSignature.verify(signedData))

        signedData = cryptoService.signData(secondKeyPair.public, payload)
        ecdsaSignature.initVerify(secondKeyPair.public)
        ecdsaSignature.update(payload)
        assertTrue(ecdsaSignature.verify(signedData))
    }
}
