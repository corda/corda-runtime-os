package net.corda.p2p.gateway.messaging.http

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class TrustStoresMapTest {
    private val subscription = mock<CompactedSubscription<String, GatewayTruststore>>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val creteResources = AtomicReference<(resources: ResourcesHolder) -> CompletableFuture<Unit>>()
    private val mockDominoTile = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        creteResources.set(context.arguments()[2] as? (resources: ResourcesHolder) -> CompletableFuture<Unit>)
    }
    private val processor = argumentCaptor<CompactedProcessor<String, GatewayTruststore>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), any()) } doReturn subscription
    }
    private val nodeConfiguration = mock<SmartConfig>()
    private val certificate = mock<Certificate>()
    private val certificateFactory = mock<CertificateFactory> {
        on { generateCertificate(any()) } doReturn certificate
    }
    private val keyStore = mock<KeyStore>()
    private val mockKeyStore = mockStatic(KeyStore::class.java).also { mockKeyStore ->
        mockKeyStore.`when`<KeyStore> {
            KeyStore.getInstance("PKCS12")
        }.doReturn(keyStore)
    }
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java)

    @AfterEach
    fun cleanUp() {
        mockKeyStore.close()
        mockDominoTile.close()
        subscriptionDominoTile.close()
    }

    private val testObject = TrustStoresMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration,
        certificateFactory
    )

    @Test
    fun `createResources will not mark as ready before getting snapshot`() {
        val future = creteResources.get()?.invoke(mock())

        assertThat(future).isNotCompleted
    }

    @Test
    fun `onSnapshot will mark as ready`() {
        val future = creteResources.get()?.invoke(mock())

        processor.firstValue.onSnapshot(emptyMap())

        assertThat(future).isCompleted
    }

    @Test
    fun `onNext with value will add store`() {
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(emptyMap())

        processor.firstValue.onNext(
            Record(Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, "group id 1", GatewayTruststore(listOf("one"))),
            null,
            emptyMap(),
        )

        assertThat(testObject.getTrustStore("group id 1")).isEqualTo(keyStore)
    }

    @Test
    fun `onNext without value will remove store`() {
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "group id 1" to GatewayTruststore(listOf("one"))
            )
        )

        processor.firstValue.onNext(
            Record(Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, "group id 1", null),
            null,
            emptyMap(),
        )

        assertThrows<IllegalArgumentException> {
            testObject.getTrustStore("group id 1")
        }
    }

    @Test
    fun `onSnapshot save the data`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "group id 1" to GatewayTruststore(listOf("one"))
            )
        )

        assertThat(testObject.getTrustStore("group id 1")).isEqualTo(keyStore)
    }

    @Test
    fun `trust store add certificates to keystore`() {
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "a" to GatewayTruststore(listOf("one", "two")),
            )
        )

        testObject.getTrustStore("a")

        verify(keyStore).setCertificateEntry("gateway-0", certificate)
        verify(keyStore).setCertificateEntry("gateway-1", certificate)
    }

    @Test
    fun `trust store will load the keystore`() {
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "a" to GatewayTruststore(listOf("one", "two")),
            )
        )

        testObject.getTrustStore("a")

        verify(keyStore).load(null, null)
    }

    @Test
    fun `trust store load the correct certificate`() {
        val data = argumentCaptor<InputStream>()
        whenever(certificateFactory.generateCertificate(data.capture())).doReturn(certificate)
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "a" to GatewayTruststore(listOf("one", "two")),
            )
        )

        testObject.getTrustStore("a")

        assertThat(data.firstValue.reader().readText()).isEqualTo("one")
        assertThat(data.secondValue.reader().readText()).isEqualTo("two")
    }
}
