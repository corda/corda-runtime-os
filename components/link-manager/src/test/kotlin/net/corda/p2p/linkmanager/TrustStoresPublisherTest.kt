package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.p2p.NetworkType
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
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
import java.util.concurrent.atomic.AtomicReference

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
    private val creteResources = AtomicReference<(resources: ResourcesHolder) -> CompletableFuture<Unit>>()
    private val mockDominoTile = mockConstruction(DominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        creteResources.set(context.arguments()[2] as? (resources: ResourcesHolder) -> CompletableFuture<Unit>)
    }
    private val mockPublisher = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        whenever(mock.publish(publishedRecords.capture())).doReturn(emptyList())
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configuration = mock<SmartConfig>()
    private val instanceId = 1
    private val publisherFactory = mock<PublisherFactory>()

    private val certificates = listOf("one", "two")
    private val groupInfo = NetworkMapListener.GroupInfo(
        "groupOne",
        NetworkType.CORDA_5,
        certificates,
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
        instanceId,
    )

    @AfterEach
    fun cleanUp() {
        mockDominoTile.close()
        mockPublisher.close()
    }

    @Nested
    inner class GroupAddedTests {

        @Test
        fun `groupAdded will publish unpublished group`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())

            trustStoresPublisher.groupAdded(groupInfo)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, groupInfo.groupId, GatewayTruststore(certificates)))
            )
        }

        @Test
        fun `groupAdded will not republish certificates`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            trustStoresPublisher.groupAdded(groupInfo)

            trustStoresPublisher.groupAdded(groupInfo)

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `groupAdded will not republish certificates in different order`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            trustStoresPublisher.groupAdded(groupInfo)

            trustStoresPublisher.groupAdded(
                groupInfo.copy(
                    trustedCertificates = certificates.reversed()
                )
            )

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `groupAdded will republish new certificates`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            trustStoresPublisher.groupAdded(groupInfo)
            val certificatesTwo = listOf("two", "three")

            trustStoresPublisher.groupAdded(
                groupInfo.copy(
                    trustedCertificates = certificatesTwo
                )
            )

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, groupInfo.groupId, GatewayTruststore(certificates))),
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, groupInfo.groupId, GatewayTruststore(certificatesTwo))),
            )
        }

        @Test
        fun `publishGroupIfNeeded will wait for certificates to be published`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            val future = mock<CompletableFuture<Unit>>()
            whenever(mockPublisher.constructed().first().publish(any())).doReturn(listOf(future))

            trustStoresPublisher.groupAdded(groupInfo)

            verify(future).join()
        }

        @Test
        fun `groupAdded will not publish before the subscription started`() {
            trustStoresPublisher.groupAdded(groupInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `groupAdded will not publish before it has the snapshots`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())

            trustStoresPublisher.groupAdded(groupInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `groupAdded will publish after it has the snapshot`() {
            trustStoresPublisher.groupAdded(groupInfo)
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())

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
            val future = creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(future.isDone).isTrue
        }

        @Test
        fun `onSnapshot save the data correctly`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onSnapshot(
                mapOf(
                    groupInfo.groupId to GatewayTruststore(
                        certificates
                    )
                )
            )

            trustStoresPublisher.groupAdded(groupInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `onNext remove item from published stores`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(
                mapOf(
                    groupInfo.groupId to GatewayTruststore(
                        certificates
                    )
                )
            )

            processor.firstValue.onNext(
                Record(
                    "", groupInfo.groupId,
                    null
                ),
                null, emptyMap()
            )

            trustStoresPublisher.groupAdded(groupInfo)

            verify(mockPublisher.constructed().first()).publish(any())
        }

        @Test
        fun `onNext add item to published stores`() {
            trustStoresPublisher.start()
            creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onNext(
                Record(
                    "", groupInfo.groupId,
                    GatewayTruststore(
                        certificates
                    )
                ),
                null, emptyMap()
            )

            trustStoresPublisher.groupAdded(groupInfo)
            verify(mockPublisher.constructed().first(), never()).publish(any())
        }
    }

    @Nested
    inner class CreateResourcesTests {
        @Test
        fun `createResources start the subscription`() {
            creteResources.get().invoke(ResourcesHolder())

            verify(subscription).start()
        }

        @Test
        fun `createResources will not complete before the snapshot is ready`() {
            val future = creteResources.get().invoke(ResourcesHolder())

            assertThat(future.isDone).isFalse
        }

        @Test
        fun `createResources will remember to close the subscription`() {
            val resources = ResourcesHolder()

            creteResources.get().invoke(resources)

            resources.close()
            verify(subscription).close()
        }
    }
}
