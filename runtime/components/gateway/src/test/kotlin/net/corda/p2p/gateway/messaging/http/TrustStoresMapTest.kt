package net.corda.p2p.gateway.messaging.http

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.GatewayTruststore
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
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
    private val mockDominoTile = mockConstruction(ComplexDominoTile::class.java)
    private var future: CompletableFuture<Unit>? = null
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        future = context.arguments()[2] as CompletableFuture<Unit>
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
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
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { mock, context ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, GatewayTruststore>)).invoke()
    }

    @AfterEach
    fun cleanUp() {
        mockKeyStore.close()
        mockDominoTile.close()
        subscriptionDominoTile.close()
        blockingDominoTile.close()
    }

    private val testObject = TrustStoresMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration,
        certificateFactory
    )

    @Test
    fun `createResources will not mark as ready before getting snapshot`() {
        assertThat(future).isNotCompleted
    }

    @Test
    fun `onSnapshot will mark as ready`() {
        processor.firstValue.onSnapshot(emptyMap())

        assertThat(future).isCompleted
    }

    @Test
    fun `onNext with value will add store`() {
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(emptyMap())

        val sourceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "group id 1"
        processor.firstValue.onNext(
            Record(Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, "key", GatewayTruststore(HoldingIdentity(sourceX500Name, groupId), listOf("one"))),
            null,
            emptyMap()
        )

        assertThat(testObject.getTrustStore(MemberX500Name.parse(sourceX500Name), groupId)).isEqualTo(keyStore)
    }

    @Test
    fun `getTrustStore will use normalized X500 name`() {
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(emptyMap())

        val groupId = "group id 1"
        processor.firstValue.onNext(
            Record(
                Schemas.P2P.GATEWAY_TLS_TRUSTSTORES,
                "key",
                GatewayTruststore(
                    HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", groupId),
                    listOf("one")
                )
            ),
            null,
            emptyMap()
        )

        assertThat(
            testObject.getTrustStore(
                MemberX500Name.parse("C=GB,   CN=Alice, O=Alice Corp, L=LDN"),
                groupId
            )
        )
            .isEqualTo(keyStore)
    }

    @Test
    fun `onNext without value will remove store`() {
        val sourceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "group id 1"

        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to GatewayTruststore(HoldingIdentity(sourceX500Name, groupId), listOf("one"))
            )
        )

        processor.firstValue.onNext(
            Record(Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, "key", null),
            null,
            emptyMap()
        )

        assertThrows<IllegalArgumentException> {
            testObject.getTrustStore(MemberX500Name.parse(sourceX500Name), groupId)
        }
    }

    @Test
    fun `onSnapshot save the data`() {
        val sourceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "group id 1"
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to GatewayTruststore(HoldingIdentity(sourceX500Name, groupId), listOf("one"))
            )
        )

        assertThat(testObject.getTrustStore(MemberX500Name.parse(sourceX500Name), groupId)).isEqualTo(keyStore)
    }

    @Test
    fun `trust store add certificates to keystore`() {
        val sourceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "group id 1"
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to GatewayTruststore(HoldingIdentity(sourceX500Name, groupId), listOf("one", "two"))
            )
        )

        testObject.getTrustStore(MemberX500Name.parse(sourceX500Name), groupId)

        verify(keyStore).setCertificateEntry("gateway-0", certificate)
        verify(keyStore).setCertificateEntry("gateway-1", certificate)
    }

    @Test
    fun `trust store will load the keystore`() {
        val sourceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "group id 1"
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to GatewayTruststore(HoldingIdentity(sourceX500Name, groupId), listOf("one", "two"))
            )
        )

        testObject.getTrustStore(MemberX500Name.parse(sourceX500Name), groupId)

        verify(keyStore).load(null, null)
    }

    @Test
    fun `trust store load the correct certificate`() {
        val sourceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "group id 1"
        val data = argumentCaptor<InputStream>()
        whenever(certificateFactory.generateCertificate(data.capture())).doReturn(certificate)
        creteResources.get()?.invoke(mock())
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to GatewayTruststore(HoldingIdentity(sourceX500Name, groupId), listOf("one", "two"))
            )
        )

        testObject.getTrustStore(MemberX500Name.parse(sourceX500Name), groupId)

        assertThat(data.firstValue.reader().readText()).isEqualTo("one")
        assertThat(data.secondValue.reader().readText()).isEqualTo("two")
    }
}
