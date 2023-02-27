package net.corda.p2p.linkmanager.forwarding.gateway

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.GatewayTlsCertificates
import net.corda.p2p.linkmanager.hosting.HostingMapListener
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class TlsCertificatesPublisherTest {
    private val processor = argumentCaptor<CompactedProcessor<String, GatewayTlsCertificates>>()
    private val subscription = mock<CompactedSubscription<String, GatewayTlsCertificates>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any(),
            )
        } doReturn subscription
    }
    private val publishedRecords = argumentCaptor<List<Record<String, GatewayTlsCertificates>>>()
    private var ready: CompletableFuture<Unit>? = null
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        ready = context.arguments()[2] as CompletableFuture<Unit>
    }
    private val mockDominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
    }
    private val mockPublisher = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.publish(publishedRecords.capture())).doReturn(emptyList())
        whenever(mock.isRunning).doReturn(true)
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val publisherFactory = mock<PublisherFactory>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configuration = mock<SmartConfig>()
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, GatewayTlsCertificates>)).invoke()
    }
    private val identityInfo = HostingMapListener.IdentityInfo(
        createTestHoldingIdentity("CN=Alice, O=Bob Corp, L=LDN, C=GB", "Group1",),
        listOf("one", "two"),
        "id1",
        mock(),
        mock()
    )

    private val publisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
    )

    @AfterEach
    fun cleanUp() {
        mockPublisher.close()
        mockDominoTile.close()
        subscriptionDominoTile.close()
        blockingDominoTile.close()
    }

    @Nested
    inner class IdentityAddedTests {

        @Test
        fun `identityAdded will publish unpublished identity`() {
            publisher.start()
            processor.firstValue.onSnapshot(emptyMap())

            publisher.identityAdded(identityInfo)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(
                    Record(
                        GATEWAY_TLS_CERTIFICATES,
                        "${identityInfo.holdingIdentity.groupId}-${identityInfo.holdingIdentity.x500Name}",
                        GatewayTlsCertificates(
                            "id1",
                            identityInfo.holdingIdentity.toAvro(),
                            listOf("one", "two"),
                        )
                    )
                )
            )
        }

        @Test
        fun `identityAdded will not republish certificates`() {
            publisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            publisher.identityAdded(identityInfo)

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `identityAdded will not republish certificates in different order`() {
            publisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            publisher.identityAdded(identityInfo)

            publisher.identityAdded(
                identityInfo.copy(
                    tlsCertificates = identityInfo.tlsCertificates.reversed()
                )
            )

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `identityAdded will republish new certificates`() {
            publisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            publisher.identityAdded(identityInfo)
            val certificatesTwo = listOf("two", "three")

            publisher.identityAdded(
                identityInfo.copy(
                    tlsCertificates = certificatesTwo
                )
            )

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_CERTIFICATES, "${identityInfo.holdingIdentity.groupId}-${identityInfo.holdingIdentity.x500Name}",
                    GatewayTlsCertificates("id1", identityInfo.holdingIdentity.toAvro(), identityInfo.tlsCertificates))),
                listOf(Record(GATEWAY_TLS_CERTIFICATES, "${identityInfo.holdingIdentity.groupId}-${identityInfo.holdingIdentity.x500Name}",
                    GatewayTlsCertificates("id1", identityInfo.holdingIdentity.toAvro(), certificatesTwo))),
            )
        }

        @Test
        fun `publishIfNeeded will wait for certificates to be published`() {
            publisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            val future = mock<CompletableFuture<Unit>>()
            whenever(mockPublisher.constructed().first().publish(any())).doReturn(listOf(future))

            publisher.identityAdded(identityInfo)

            verify(future).join()
        }

        @Test
        fun `identityAdded will not publish before the subscription started`() {
            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `identityAdded will not publish before it has the snapshots`() {
            publisher.start()

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `identityAdded will not publish before the publisher is ready`() {
            publisher.start()
            whenever(mockPublisher.constructed().first().isRunning).doReturn(false)
            processor.firstValue.onSnapshot(emptyMap())

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `identityAdded will publish after it has the snapshot`() {
            publisher.identityAdded(identityInfo)
            publisher.start()

            processor.firstValue.onSnapshot(emptyMap())
            processor.firstValue.onSnapshot(emptyMap())

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }
    }

    @Nested
    inner class ProcessorTests {
        @Test
        fun `onSnapshot mark the publisher as ready`() {
            publisher.start()

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(ready!!.isDone).isTrue
        }

        @Test
        fun `onSnapshot save the data correctly`() {
            publisher.start()

            processor.firstValue.onSnapshot(
                mapOf(
                    "${identityInfo.holdingIdentity.groupId}-${identityInfo.holdingIdentity.x500Name}" to GatewayTlsCertificates(
                        identityInfo.tlsTenantId,
                        identityInfo.holdingIdentity.toAvro(),
                        identityInfo.tlsCertificates,
                    )
                )
            )

            publisher.identityAdded(identityInfo)
            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `onNext remove item from published stores`() {
            publisher.start()
            processor.firstValue.onSnapshot(
                mapOf(
                    "Group1-Alice" to GatewayTlsCertificates(
                        identityInfo.tlsTenantId,
                        identityInfo.holdingIdentity.toAvro(),
                        identityInfo.tlsCertificates,
                    )
                )
            )

            processor.firstValue.onNext(
                Record(
                    "", "Group1-Alice",
                    null
                ),
                null, emptyMap()
            )

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first()).publish(any())
        }

        @Test
        fun `onNext add item to published stores`() {
            publisher.start()

            processor.firstValue.onNext(
                Record(
                    "",
                    "Group1-Alice",
                    GatewayTlsCertificates(
                        identityInfo.tlsTenantId,
                        identityInfo.holdingIdentity.toAvro(),
                        identityInfo.tlsCertificates,
                    )
                ),
                null, emptyMap()
            )

            publisher.identityAdded(identityInfo)
            verify(mockPublisher.constructed().first(), never()).publish(any())
        }
    }
    @Nested
    inner class CreateResourcesTests {
        @Test
        fun `createResources will not complete before the snapshot is ready`() {
            assertThat(ready!!.isDone).isFalse
        }
    }
}
