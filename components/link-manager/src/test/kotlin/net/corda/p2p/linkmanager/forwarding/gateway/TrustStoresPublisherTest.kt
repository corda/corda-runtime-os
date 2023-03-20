package net.corda.p2p.linkmanager.forwarding.gateway

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.GatewayTruststore
import net.corda.schema.Schemas.P2P.GATEWAY_TLS_TRUSTSTORES
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

class TrustStoresPublisherTest {
    private val processor = argumentCaptor<CompactedProcessor<String, GatewayTruststore>>()
    private val subscription = mock<CompactedSubscription<String, GatewayTruststore>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any(),
            )
        } doReturn subscription
    }
    private val publishedRecords = argumentCaptor<List<Record<String, GatewayTruststore>>>()
    private var ready: CompletableFuture<Unit>? = null
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        ready = context.arguments()[2] as CompletableFuture<Unit>
    }
    private val mockDominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val mockPublisher = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.publish(publishedRecords.capture())).doReturn(emptyList())
        whenever(mock.isRunning).doReturn(true)
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configuration = mock<SmartConfig>()
    private val publisherFactory = mock<PublisherFactory>()
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedProcessor<String, GatewayTruststore>)).invoke()
    }

    private val certificates = listOf("one", "two")
    private val holdingIdentity = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "groupOne")
    private val parameters = mock<GroupPolicy.P2PParameters> {
        on { tlsTrustRoots } doReturn certificates
    }
    private val groupPolicy = mock<GroupPolicy> {
        on { p2pParameters } doReturn parameters
    }

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
    )

    @AfterEach
    fun cleanUp() {
        mockDominoTile.close()
        blockingDominoTile.close()
        mockPublisher.close()
        subscriptionDominoTile.close()
    }

    @Nested
    inner class GroupAddedTests {

        @Test
        fun `groupAdded will publish unpublished group`() {
            trustStoresPublisher.start()
            processor.firstValue.onSnapshot(emptyMap())

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES,
                    "${holdingIdentity.x500Name}-${holdingIdentity.groupId}",
                    GatewayTruststore(holdingIdentity.toAvro(), certificates)
                ))
            )
        }

        @Test
        fun `groupAdded will not republish certificates`() {
            trustStoresPublisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `groupAdded will not republish certificates in different order`() {
            val parameters = mock<GroupPolicy.P2PParameters> {
                on { tlsTrustRoots } doReturn certificates.reversed()
            }
            whenever(groupPolicy.p2pParameters).doReturn(parameters)
            trustStoresPublisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            trustStoresPublisher.groupAdded(
                holdingIdentity,
                groupPolicy,
            )

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `groupAdded will republish new certificates`() {
            trustStoresPublisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)
            val certificatesTwo = listOf("two", "three")
            val parameters = mock<GroupPolicy.P2PParameters> {
                on { tlsTrustRoots } doReturn certificatesTwo
            }
            whenever(groupPolicy.p2pParameters).doReturn(parameters)

            trustStoresPublisher.groupAdded(
                holdingIdentity, groupPolicy
            )

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(
                    Record(GATEWAY_TLS_TRUSTSTORES,
                    "${holdingIdentity.x500Name}-${holdingIdentity.groupId}",
                    GatewayTruststore(holdingIdentity.toAvro(), certificates))
                ),
                listOf(
                    Record(GATEWAY_TLS_TRUSTSTORES,
                    "${holdingIdentity.x500Name}-${holdingIdentity.groupId}",
                    GatewayTruststore(holdingIdentity.toAvro(), certificatesTwo))
                ),
            )
        }

        @Test
        fun `publishGroupIfNeeded will wait for certificates to be published`() {
            trustStoresPublisher.start()
            processor.firstValue.onSnapshot(emptyMap())
            val future = mock<CompletableFuture<Unit>>()
            whenever(mockPublisher.constructed().first().publish(any())).doReturn(listOf(future))

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(future).join()
        }

        @Test
        fun `groupAdded will not publish before the subscription started`() {
            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `groupAdded will not publish before it has the snapshots`() {
            trustStoresPublisher.start()

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `groupAdded will not publish before the publisher is ready`() {
            trustStoresPublisher.start()
            whenever(mockPublisher.constructed().first().isRunning).doReturn(false)
            processor.firstValue.onSnapshot(emptyMap())

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `groupAdded will publish after it has the snapshot`() {
            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)
            trustStoresPublisher.start()

            processor.firstValue.onSnapshot(emptyMap())
            processor.firstValue.onSnapshot(emptyMap())

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }
    }

    @Nested
    inner class ProcessorTests {
        @Test
        fun `onSnapshot mark the publisher as ready`() {
            trustStoresPublisher.start()

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(ready!!.isDone).isTrue
        }

        @Test
        fun `onSnapshot save the data correctly`() {
            trustStoresPublisher.start()

            processor.firstValue.onSnapshot(
                mapOf(
                    "${holdingIdentity.x500Name}-${holdingIdentity.groupId}" to
                    GatewayTruststore(holdingIdentity.toAvro(), certificates)
                )
            )

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `onNext remove item from published stores`() {
            trustStoresPublisher.start()
            processor.firstValue.onSnapshot(
                mapOf(
                    "${holdingIdentity.x500Name}-${holdingIdentity.groupId}" to
                    GatewayTruststore(holdingIdentity.toAvro(), certificates)
                )
            )

            processor.firstValue.onNext(
                Record(
                    "", "${holdingIdentity.x500Name}-${holdingIdentity.groupId}",
                    null
                ),
                null, emptyMap()
            )

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)

            verify(mockPublisher.constructed().first()).publish(any())
        }

        @Test
        fun `onNext add item to published stores`() {
            trustStoresPublisher.start()

            processor.firstValue.onNext(
                Record(
                    "", "${holdingIdentity.x500Name}-${holdingIdentity.groupId}",
                    GatewayTruststore(holdingIdentity.toAvro(), certificates)
                ),
                null, emptyMap()
            )

            trustStoresPublisher.groupAdded(holdingIdentity, groupPolicy)
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
