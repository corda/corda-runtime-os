package net.corda.p2p.gateway.messaging.http

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
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
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

class TrustStoresMapTest {
    private val subscription = mock<CompactedSubscription<String, GatewayTruststore>>()
    private val lifecycleEventHandler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock< LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            lifecycleEventHandler.firstValue.processEvent(it.getArgument(0), mock())
        }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), lifecycleEventHandler.capture()) } doReturn coordinator
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

    @AfterEach
    fun cleanUp() {
        mockKeyStore.close()
    }

    private val testObject = TrustStoresMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration,
        12,
        certificateFactory
    )

    @Test
    fun `createResources will start the subscription`() {
        testObject.start()

        verify(subscription).start()
    }

    @Test
    fun `createResources will not mark as ready before getting snapshot`() {
        testObject.start()

        assertThat(testObject.isRunning).isFalse
    }

    @Test
    fun `onSnapshot will mark as ready`() {
        testObject.start()

        processor.firstValue.onSnapshot(emptyMap())

        assertThat(testObject.isRunning).isTrue
    }

    @Test
    fun `stop will stop the subscription`() {
        testObject.start()
        processor.firstValue.onSnapshot(emptyMap())

        testObject.stop()

        verify(subscription).stop()
    }

    @Test
    fun `onNext with value will add store`() {
        testObject.start()
        processor.firstValue.onSnapshot(emptyMap())

        processor.firstValue.onNext(
            Record(Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, "hash", GatewayTruststore(listOf("one"))),
            null,
            emptyMap(),
        )

        assertThat(testObject.getTrustStore("hash")).isEqualTo(keyStore)
    }

    @Test
    fun `onNext without value will remove store`() {
        testObject.start()
        processor.firstValue.onSnapshot(
            mapOf(
                "hash" to GatewayTruststore(listOf("one"))
            )
        )

        processor.firstValue.onNext(
            Record(Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, "hash", null),
            null,
            emptyMap(),
        )

        assertThrows<IllegalArgumentException> {
            testObject.getTrustStore("hash")
        }
    }

    @Test
    fun `onSnapshot save the data`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "hash" to GatewayTruststore(listOf("one"))
            )
        )

        assertThat(testObject.getTrustStore("hash")).isEqualTo(keyStore)
    }

    @Test
    fun `trust store add certificates to keystore`() {
        testObject.start()
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
        testObject.start()
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
        testObject.start()
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
