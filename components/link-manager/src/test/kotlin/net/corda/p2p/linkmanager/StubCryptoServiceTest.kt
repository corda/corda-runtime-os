package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ECDSA_SIGNATURE_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RSA_SIGNATURE_ALGO
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.CompletableFuture

class StubCryptoServiceTest {

    private var clientProcessor: CompactedProcessor<String, KeyPairEntry>? = null

    private val subscriptionFactory = mock<SubscriptionFactory>() {
        on { createCompactedSubscription(any(), any<CompactedProcessor<String, KeyPairEntry>>(), any()) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            clientProcessor = invocation.arguments[1] as CompactedProcessor<String, KeyPairEntry>
            mock()
        }
    }

    private val coordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val resourcesHolder = mock<ResourcesHolder>()
    private lateinit var createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
    private val dominoTile = Mockito.mockConstruction(DominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((ResourcesHolder) -> CompletableFuture<Unit>)
        whenever(mock.isRunning).doReturn(true)
    }
    private val subscriptionTile = Mockito.mockConstruction(SubscriptionDominoTile::class.java)

    private val cryptoService = StubCryptoService(coordinatorFactory, subscriptionFactory, 0, SmartConfigImpl.empty()).apply {
        start()
    }

    private val rsaKeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    private val ecdsaKeyPairGenerator = KeyPairGenerator.getInstance("EC")

    private val rsaSignature = Signature.getInstance(RSA_SIGNATURE_ALGO)
    private val ecdsaSignature = Signature.getInstance(ECDSA_SIGNATURE_ALGO)

    private val firstKeyPair = rsaKeyPairGenerator.genKeyPair()
    private val secondKeyPair = ecdsaKeyPairGenerator.genKeyPair()

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        subscriptionTile.close()
        resourcesHolder.close()
    }

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
        createResources(resourcesHolder)
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

    @Test
    fun `onSnapshot completes the resource future`() {
        val future = createResources(resourcesHolder)
        clientProcessor!!.onSnapshot(emptyMap())
        Assertions.assertThat(future.isDone).isTrue
        Assertions.assertThat(future.isCompletedExceptionally).isFalse
    }
}
